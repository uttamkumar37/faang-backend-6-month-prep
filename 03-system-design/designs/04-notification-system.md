# Design a Notification System

## Requirements

**Functional:**
- Send push, email, and SMS notifications.
- Support urgent/normal priority levels.
- Retry failed sends.
- Rate limit per user (no spam).
- User opt-out / preferences.

**Non-Functional:**
- 10M notifications/day → ~115/sec average.
- Peak: 1M/min (sporting event, breaking news).
- Delivery SLA: 30 seconds for urgent, 5 minutes for normal.
- At-least-once delivery guarantee.

---

## High-Level Architecture

```
[Services] → Notification API → [Kafka Topics by Priority]
                                        ↓
                               Notification Processors
                                  ↙       ↓       ↘
                           Push(FCM)  Email(SES)  SMS(Twilio)
                                  ↘       ↓       ↙
                                  Delivery Status DB
                                        ↓
                               Retry Queue (DLQ)
```

---

## Component Design

### Notification Request API

```json
POST /api/v1/notifications
{
  "recipients": ["user_id_1", "user_id_2"],
  "channels": ["push", "email"],
  "priority": "urgent",
  "templateId": "order_confirmed",
  "data": { "orderId": "ORD-123", "amount": 49.99 }
}
```

### User Preference Store

```sql
CREATE TABLE user_notification_prefs (
    user_id    BIGINT,
    channel    VARCHAR(10),  -- push/email/sms
    enabled    BOOLEAN DEFAULT true,
    quiet_start TIME,
    quiet_end   TIME,
    PRIMARY KEY (user_id, channel)
);
```

### Kafka Topics

```
notifications.urgent   → 12 partitions → low-latency processors
notifications.normal   → 24 partitions → batch processors
notifications.dlq      → dead letter queue for failed sends
```

### Channel Workers

```java
@KafkaListener(topics = "notifications.urgent")
public void handleUrgent(NotificationEvent event) {
    User user = userService.getUser(event.getUserId());
    if (!user.isOptedIn(event.getChannel())) return;

    try {
        channelAdapter.send(event);
        trackDelivery(event.getId(), DeliveryStatus.DELIVERED);
    } catch (ChannelException e) {
        if (event.getRetryCount() < 3) {
            retryQueue.schedule(event, exponentialBackoff(event.getRetryCount()));
        } else {
            trackDelivery(event.getId(), DeliveryStatus.FAILED);
            alertOpsTeam(event);
        }
    }
}
```

---

## Retry Strategy

```
Attempt 1: immediate
Attempt 2: 1 minute
Attempt 3: 5 minutes
Attempt 4: 30 minutes → move to DLQ
```

Exponential backoff: `delay = base × 2^attempt + jitter`

---

## Deduplication

Problem: Kafka at-least-once → same notification sent twice.  
Solution: idempotency key in Redis.

```
Key: "notif:sent:{notifId}:{channel}"
SET notif:sent:abc123:push "1" EX 86400   (TTL 1 day)
If key exists → skip, already sent
```

---

## Template Engine

```
Template: "Your order {orderId} is confirmed. Amount: {amount}"
Store templates in DB or config.
Render with Mustache/Freemarker at send time.
```

---

## Monitoring

```yaml
alerts:
  - metric: notification_delivery_lag_seconds > 30
    severity: critical
  - metric: notification_error_rate > 0.01
    severity: warning
  - metric: dlq_depth > 1000
    severity: warning
```

---

## Scale Handling

| Problem | Solution |
|---|---|
| 1M/min spike | Kafka buffers, auto-scale consumers |
| Email provider limits | Multiple providers (SES + SendGrid), round-robin |
| User pref lookup hot path | Cache in Redis (5 min TTL) |
| Analytics / delivery stats | Write to Cassandra (time-series) |

---

## Project Structure

```
notification-system/
├── src/main/java/com/notification/
│   ├── NotificationApplication.java
│   ├── api/
│   │   └── NotificationController.java   # POST /v1/notifications
│   ├── service/
│   │   ├── NotificationService.java      # validate + publish to Kafka
│   │   ├── UserPreferenceService.java    # Redis-cached opt-in/out
│   │   └── ChannelRouter.java            # push / email / SMS dispatch
│   ├── consumer/
│   │   ├── UrgentNotificationConsumer.java   # Kafka topic: notif.urgent
│   │   └── NormalNotificationConsumer.java   # Kafka topic: notif.normal
│   ├── channel/
│   │   ├── PushChannel.java              # FCM integration
│   │   ├── EmailChannel.java             # SES integration
│   │   └── SmsChannel.java              # Twilio integration
│   └── model/
│       └── Notification.java             # id, userId, type, channel, payload
└── pom.xml
```

## Core Implementation

```java
// Notification model
public record Notification(
    String id, String userId, String type,
    String channel, String title, String body,
    int priority  // 1=urgent, 2=normal
) {}

// Service: validate preferences + publish to Kafka
@Service
public class NotificationService {
    @Autowired KafkaTemplate<String, Notification> kafka;
    @Autowired UserPreferenceService prefs;

    public void send(Notification n) {
        if (!prefs.isOptedIn(n.userId(), n.channel())) {
            log.info("User {} opted out of {}", n.userId(), n.channel());
            return;
        }
        String topic = n.priority() == 1 ? "notif.urgent" : "notif.normal";
        kafka.send(topic, n.userId(), n)  // key=userId → same partition = ordering
            .exceptionally(ex -> { log.error("Kafka publish failed", ex); return null; });
    }
}

// Consumer: at-least-once with retry + DLQ
@Service
public class UrgentNotificationConsumer {
    @Autowired ChannelRouter router;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2),
                    dltTopicSuffix = ".dlq")
    @KafkaListener(topics = "notif.urgent", groupId = "notif-urgent-group")
    public void consume(Notification n) {
        router.dispatch(n);
    }

    @DltHandler
    public void handleDlt(Notification n) {
        log.error("FAILED after retries — moved to DLQ: userId={} type={}", n.userId(), n.type());
        // persist to dead_letter_table for manual review
    }
}

// Channel router: dispatches to the right provider
@Service
public class ChannelRouter {
    @Autowired PushChannel push;
    @Autowired EmailChannel email;
    @Autowired SmsChannel sms;

    public void dispatch(Notification n) {
        switch (n.channel()) {
            case "push"  -> push.send(n);
            case "email" -> email.send(n);
            case "sms"   -> sms.send(n);
            default -> throw new IllegalArgumentException("Unknown channel: " + n.channel());
        }
    }
}

// User preference cache — Redis backed
@Service
public class UserPreferenceService {
    @Autowired RedisTemplate<String, String> redis;
    @Autowired UserPrefRepository repo;

    public boolean isOptedIn(String userId, String channel) {
        String key = "pref:" + userId + ":" + channel;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return "1".equals(cached);

        boolean optedIn = repo.isOptedIn(userId, channel);
        redis.opsForValue().set(key, optedIn ? "1" : "0", Duration.ofMinutes(5));
        return optedIn;
    }
}
```
