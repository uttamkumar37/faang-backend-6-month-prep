# Design Twitter / Social Feed

## Requirements

**Functional:**
- Post a tweet (text, images).
- Follow / unfollow users.
- View home timeline (tweets from followed users).
- Like, retweet.
- Search tweets.

**Non-Functional:**
- 300M daily active users.
- 500M tweets/day writes.
- Read:write = 100:1 (read-heavy).
- Home timeline load < 200ms.
- Eventually consistent.

---

## Core Challenge: Feed Generation

Two approaches:

### Approach 1: Fan-out on Write (Push Model)

When user A tweets → push tweet ID to all A's followers' home feed caches.

```
Tweet written → Kafka → Fan-out Service
                            ↓
                    For each follower:
                    Redis LPUSH follower:feed:{userId} tweetId
                    LTRIM follower:feed:{userId} 0 999  (keep 1000)
```

**Pros**: Feed reads are O(1) from Redis.  
**Cons**: celebrities with 10M followers → 10M writes per tweet.

### Approach 2: Fan-out on Read (Pull Model)

When user loads timeline → query who they follow → fetch recent tweets from each.

```
GET /timeline/{userId}
→ Get followed_users (up to 200)
→ For each: GET user:tweets:{followedId} LIMIT 10
→ Merge + sort by timestamp
```

**Pros**: No write amplification.  
**Cons**: Read is expensive for users following many accounts.

### Hybrid (Twitter's actual approach)

```
Fan-out on write for normal users (< 10K followers).
Fan-out on read for celebrities (> 10K followers).
On timeline load: merge pre-built feed + celebrity fetches.
```

---

## System Architecture

```
Mobile/Web
    ↓
API Gateway (auth, rate limit)
    ↓
Tweet Service ─────────── Fan-out Service
    ↓                           ↓
Tweet DB (Cassandra)      Timeline Cache (Redis)
    ↓                           ↓
Media Service (S3)        User Service (MySQL)
    ↓                           ↓
Search Service            Notification Service
(Elasticsearch)
```

---

## Data Models

### Tweet Storage (Cassandra)

```
Table: tweets_by_user
Partition key: user_id
Clustering key: tweet_id DESC (time-ordered)
Columns: tweet_id, content, media_urls, likes, retweets, created_at
```

Cassandra optimized for time-series, append-heavy workloads.

### Timeline Cache (Redis)

```
Key: timeline:{userId}
Type: Sorted Set (score = timestamp)
Members: tweet_ids

ZADD timeline:123 1640000000 "tweet456"
ZRANGE timeline:123 0 50 REV WITHSCORES  → latest 50 tweets
```

---

## Scale Numbers

```
500M tweets/day = 5800 tweets/sec writes
50B timeline reads/day = 578K reads/sec

Tweet storage: 500M × 200 bytes = 100 GB/day = 36 TB/year
Redis timeline: 300M users × 1000 tweet IDs × 8 bytes = 2.4 TB
```

---

## Search

- Elasticsearch for full-text tweet search.
- Kafka consumer indexes tweets asynchronously.
- Trending topics: count term frequency in rolling 1-hour window using Spark Streaming or Flink.

---

## Interview Tips

- Lead with the fan-out tradeoff — it's the central design decision.
- Bring up celebrities explicitly (Kylie Jenner test).
- Cursors for pagination (not offsets — timeline is dynamic).
- Media: store in S3 with CDN, store only URLs in DB.
- Likes/retweets: counters in Redis, batch-flush to DB every minute.

---

## Project Structure

```
twitter-feed/
├── src/main/java/com/twitter/
│   ├── TwitterApplication.java
│   ├── tweet/
│   │   ├── TweetController.java       # POST /v1/tweets
│   │   └── TweetService.java         # save tweet + trigger fanout
│   ├── fanout/
│   │   ├── FanoutService.java         # push tweetId to follower feeds
│   │   └── FanoutConsumer.java        # Kafka consumer for fan-out events
│   ├── timeline/
│   │   ├── TimelineController.java    # GET /v1/users/{id}/timeline
│   │   └── TimelineService.java      # merge pre-computed + celebrity feeds
│   ├── follow/
│   │   └── FollowRepository.java      # follower graph (user_id, follows_id)
│   └── model/
│       └── Tweet.java                 # id, authorId, content, createdAt
└── pom.xml
```

## Core Implementation

```java
// Tweet creation + fan-out event publish
@Service
public class TweetService {
    @Autowired TweetRepository tweetRepo;
    @Autowired KafkaTemplate<String, TweetEvent> kafka;
    @Value("${fanout.celebrity-threshold:100000}") int celebrityThreshold;
    @Autowired FollowRepository followRepo;

    public Tweet postTweet(String authorId, String content) {
        Tweet tweet = tweetRepo.save(new Tweet(authorId, content, Instant.now()));

        // Only fan-out for non-celebrities (< threshold followers)
        long followerCount = followRepo.countFollowers(authorId);
        if (followerCount < celebrityThreshold) {
            kafka.send("tweet.fanout", authorId,
                new TweetEvent(tweet.getId(), authorId, tweet.getCreatedAt()));
        }
        // Celebrities: followers pull feed at read time to avoid hot-key stampede
        return tweet;
    }
}

// Fan-out consumer: writes tweetId to each follower's feed in Redis
@Service
public class FanoutConsumer {
    @Autowired FollowRepository followRepo;
    @Autowired RedisTemplate<String, Long> redis;
    private static final int FEED_MAX_SIZE = 800;
    private static final Duration FEED_TTL  = Duration.ofDays(7);

    @KafkaListener(topics = "tweet.fanout", groupId = "fanout-group",
                   concurrency = "10")  // 10 partitions = 10 concurrent consumers
    public void consume(TweetEvent event) {
        // Fetch follower IDs in batches (could be millions for popular users)
        List<String> followers = followRepo.getFollowers(event.authorId());

        // Batched Redis pipeline for efficiency
        redis.executePipelined((RedisCallback<?>) conn -> {
            for (String followerId : followers) {
                String feedKey = "feed:" + followerId;
                // Sorted set: score = epoch millis, value = tweetId
                conn.zAdd(feedKey.getBytes(),
                    event.createdAt().toEpochMilli(),
                    String.valueOf(event.tweetId()).getBytes());
                // Trim feed to max size (evict oldest)
                conn.zRemRange(feedKey.getBytes(), 0, -(FEED_MAX_SIZE + 1));
                conn.expire(feedKey.getBytes(), FEED_TTL.getSeconds());
            }
            return null;
        });
    }
}

// Timeline read: merge pre-built feed + celebrity tweets
@Service
public class TimelineService {
    @Autowired RedisTemplate<String, String> redis;
    @Autowired TweetRepository tweetRepo;
    @Autowired FollowRepository followRepo;

    public List<Tweet> getTimeline(String userId, String cursor, int limit) {
        String feedKey = "feed:" + userId;

        // 1. Read pre-computed feed (tweet IDs sorted by time desc)
        long maxScore = cursor != null ? Long.parseLong(cursor) - 1 : Long.MAX_VALUE;
        Set<String> tweetIds = redis.opsForZSet()
            .reverseRangeByScore(feedKey, 0, maxScore, 0, limit);

        // 2. Merge celebrity tweets (pulled on read)
        List<String> celebrities = followRepo.getCelebrityFollowing(userId);
        List<Tweet> celebTweets = tweetRepo.findRecentByAuthors(celebrities, limit);

        // 3. Hydrate tweet IDs → full tweet objects
        List<Tweet> feedTweets = tweetRepo.findAllById(
            tweetIds.stream().map(Long::parseLong).toList());

        // 4. Merge + sort by createdAt desc
        return Stream.concat(feedTweets.stream(), celebTweets.stream())
            .sorted(Comparator.comparing(Tweet::getCreatedAt).reversed())
            .limit(limit)
            .toList();
    }
}
```
