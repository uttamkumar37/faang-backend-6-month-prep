# System Design: Video Streaming Service (YouTube / Netflix)

**Difficulty:** Hard | **Frequency:** Very High (Google, Netflix, Meta, Amazon)

---

## 1. Requirements Clarification

### Functional
- Users can **upload** video files (any resolution)
- Users can **stream** videos with adaptive bitrate (360p, 720p, 1080p, 4K)
- Videos have **metadata** (title, description, tags, channel)
- Support **search** by title / tags
- Track **view counts** and **likes**
- Support **comments** (out of scope for deep-dive)
- Support **recommendations** (out of scope)

### Non-Functional
- DAU: 1 billion users; 500 hours of video uploaded per minute
- Availability: 99.99% for streaming; uploads can tolerate brief downtime
- Latency: video starts within 1–2 seconds of play request
- Durability: no video data loss ever
- Throughput: Netflix serves ~700 Gbps at peak
- **Read-heavy** — uploads rare, views extremely common (100:1 ratio)

---

## 2. Capacity Estimation

```
Uploads:
  500 hours/min = 30,000 min of video/min
  1 min HD video = 100 MB → 3 TB/min ingested raw
  After encoding (4 qualities × 4×): ~50 TB/min stored

Views:
  1B DAU × 5 videos/day = 5B views/day
  5B / 86,400 ≈ 58,000 views/sec average
  Peak: ~200,000 views/sec

Bandwidth:
  200K × 2 Mbps (average stream) = 400 Gbps peak
  Must use CDN — no central servers can serve this

Storage:
  30,000 GB/min × 60 × 24 × 365 × 5 ≈ 79 Exabytes over 5 years
  (Compressed, deduplicated in practice)
```

---

## 3. High-Level Architecture

```
 UPLOAD PATH
 ┌────────┐   chunked    ┌─────────────┐  raw video  ┌─────────┐
 │ Client │ ──────────►  │ Upload API  │ ──────────► │  S3     │
 └────────┘              └─────────────┘             │  Raw    │
                               │ event               └────┬────┘
                               ▼                          │
                         ┌──────────┐         ┌──────────▼──────────┐
                         │  Kafka   │ ──────► │  Encoding Workers   │
                         └──────────┘         │  (FFmpeg cluster)   │
                                              └──────────┬──────────┘
                                                         │ encoded (multi-quality)
                                                         ▼
                                              ┌──────────────────┐
                                              │  S3 / CDN Origin │
                                              └──────────────────┘

 STREAM PATH
 ┌────────┐  GET manifest  ┌──────────┐  metadata  ┌──────────┐
 │ Client │ ─────────────► │ Stream   │ ─────────► │   DB     │
 └────────┘                │   API    │            └──────────┘
     ▲                     └──────────┘
     │                          │ CDN URL
     │                          ▼
     │                     ┌──────────────────┐
     └─────────────────────│   CDN Edge Node  │
          video segments   └──────────────────┘
```

---

## 4. Core Components

### 4.1 Upload Service
- Accept chunked multipart uploads (4–16 MB chunks)
- Each chunk: `POST /upload/{uploadId}/chunk/{chunkIndex}`
- Client retries failed chunks independently
- On last chunk: publish `VideoUploaded` event to Kafka
- Store raw video in S3 with `video/{videoId}/raw`

### 4.2 Encoding Pipeline (Transcoding)
- Workers consume `VideoUploaded` events from Kafka
- Transcode to: 360p (500 Kbps), 720p (2 Mbps), 1080p (5 Mbps), 4K (15 Mbps)
- Use FFmpeg in containerized workers (horizontally scalable)
- Output: HLS (`.m3u8` manifest + `.ts` segments) or DASH format
- Upload encoded segments to S3 CDN origin
- On completion: publish `VideoEncoded` event → update video status in DB

### 4.3 Streaming with Adaptive Bitrate (ABR)
- Client fetches `.m3u8` master playlist listing multiple quality streams
- Client player monitors download speed → switches quality seamlessly
- Video segments: 2–6 seconds each (small = fast start, large = fewer requests)
- CDN serves segments from edge node closest to user
- Cache-Control: `max-age=31536000` (segments are immutable)

### 4.4 Metadata Service
- Store: title, description, channel, duration, status, created_at, view_count
- PostgreSQL for metadata (relational, consistent)
- Elasticsearch for full-text search by title/tags
- Status machine: `UPLOADING → PROCESSING → PUBLISHED | FAILED`

### 4.5 View Count Service
- Challenge: global counter at 200K writes/sec → DB won't survive
- Solution: Kafka + stream processing
  1. Client sends view event to Kafka
  2. Flink/Kafka Streams aggregates per video_id per 30s window
  3. Batch-updates the counter in DB every 30s
- Display: eventually consistent (OK — "1.2M views" vs "1,200,003" doesn't matter)

---

## 5. Database Design

### Videos Table (PostgreSQL)
```sql
CREATE TABLE videos (
    id          UUID PRIMARY KEY,
    channel_id  UUID NOT NULL,
    title       VARCHAR(250) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  -- UPLOADING, PROCESSING, PUBLISHED, FAILED
    duration_s  INTEGER,
    view_count  BIGINT DEFAULT 0,
    like_count  BIGINT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT NOW(),
    INDEX idx_channel_id (channel_id),
    INDEX idx_status_created (status, created_at)
);
```

### Video Segments (S3 path pattern — no table needed)
```
s3://video-cdn/{videoId}/hls/
  ├── master.m3u8
  ├── 360p/
  │   ├── playlist.m3u8
  │   └── seg_0000.ts, seg_0001.ts, ...
  ├── 720p/
  └── 1080p/
```

---

## 6. CDN Strategy

- **Upload origin**: S3 bucket in primary region
- **CDN distribution**: CloudFront / Akamai with edge PoPs worldwide
- **Cache key**: URL path (videoId + quality + segment number) — globally unique
- **TTL**: segments = 1 year (immutable); manifest = 5 min (may change)
- **Pre-warming**: for popular videos, trigger CDN pre-fetch before publish

---

## 7. Deep Dive: Handling Scale

### Slow Upload Resumability
- Client tracks which chunks succeeded
- `GET /upload/{uploadId}/status` returns completed chunk indexes
- Client resumes from last failed chunk
- Server uses S3 Multipart Upload under the hood

### Encoding Bottleneck
- Kafka enables decoupled, auto-scaled encoding workers
- Priority queue: live streams > new uploads > re-encoding jobs
- Worker picks up job → claims it in DB → encodes → releases
- Dead letter queue for failed encodes after 3 retries

### Hot Videos (Viral Content)
- Even CDN edges have limits for extreme viral events
- Solution: multiple CDN providers + anycast routing
- Pre-push to CDN edges when video is trending

### View Count Accuracy
```
Kafka topic: view-events (partitioned by video_id)
Flink job: tumbling 30s window → SUM(views) per video_id
Writer: UPSERT into view_counts table
Threshold: if delta > 10K in 10s → real-time path (count++); else batch
```

---

## 8. Project Structure

```
video-streaming-service/
├── upload-service/
│   ├── UploadController.java
│   ├── ChunkedUploadService.java
│   └── S3UploadClient.java
├── encoding-worker/
│   ├── VideoEncodingConsumer.java
│   ├── FFmpegTranscoder.java
│   └── HlsSegmentUploader.java
├── stream-api/
│   ├── StreamController.java
│   └── ManifestBuilderService.java
├── metadata-service/
│   ├── VideoMetadataService.java
│   └── VideoSearchService.java       (Elasticsearch)
└── viewcount-processor/
    └── ViewCountAggregator.java       (Kafka Streams)
```

---

## 9. Core Implementation

```java
package systemdesign.videostreaming;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class VideoStreamingExamples {

    // --- Chunked Upload State Machine ---

    enum VideoStatus { UPLOADING, PROCESSING, PUBLISHED, FAILED }

    static class UploadSession {
        final String uploadId;
        final String videoId;
        final int totalChunks;
        final Set<Integer> completedChunks = ConcurrentHashMap.newKeySet();
        volatile VideoStatus status = VideoStatus.UPLOADING;

        UploadSession(String uploadId, String videoId, int totalChunks) {
            this.uploadId = uploadId;
            this.videoId  = videoId;
            this.totalChunks = totalChunks;
        }

        boolean receiveChunk(int index, byte[] data) {
            // In real code: upload to S3 here
            completedChunks.add(index);
            return completedChunks.size() == totalChunks;
        }

        List<Integer> missingChunks() {
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                if (!completedChunks.contains(i)) missing.add(i);
            }
            return missing;
        }
    }

    static class ChunkedUploadService {
        private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

        public String initUpload(String videoId, int totalChunks) {
            String uploadId = UUID.randomUUID().toString();
            sessions.put(uploadId, new UploadSession(uploadId, videoId, totalChunks));
            System.out.println("Upload initiated: " + uploadId + " (" + totalChunks + " chunks)");
            return uploadId;
        }

        public UploadResult receiveChunk(String uploadId, int chunkIndex, byte[] data) {
            UploadSession session = sessions.get(uploadId);
            if (session == null) return new UploadResult(false, "Unknown uploadId", false);

            boolean allDone = session.receiveChunk(chunkIndex, data);
            System.out.printf("Chunk %d/%d received for %s%n",
                session.completedChunks.size(), session.totalChunks, uploadId);

            if (allDone) {
                session.status = VideoStatus.PROCESSING;
                publishEncodingEvent(session.videoId); // fire-and-forget to Kafka
                return new UploadResult(true, "Upload complete, encoding started", true);
            }
            return new UploadResult(true, "Chunk accepted", false);
        }

        public List<Integer> getMissingChunks(String uploadId) {
            UploadSession session = sessions.get(uploadId);
            return session != null ? session.missingChunks() : Collections.emptyList();
        }

        private void publishEncodingEvent(String videoId) {
            System.out.println("[Kafka] VideoUploaded event published for: " + videoId);
        }

        record UploadResult(boolean success, String message, boolean uploadComplete) {}
    }

    // --- Encoding Worker (simulated) ---

    enum VideoQuality { Q360P, Q720P, Q1080P, Q4K }

    record EncodedSegment(String videoId, VideoQuality quality, int segmentIndex, String s3Path) {}

    static class VideoEncodingWorker {
        private static final Map<VideoQuality, String> BITRATES = Map.of(
            VideoQuality.Q360P,  "500k",
            VideoQuality.Q720P,  "2000k",
            VideoQuality.Q1080P, "5000k",
            VideoQuality.Q4K,    "15000k"
        );

        public List<EncodedSegment> encode(String videoId, byte[] rawVideo) {
            List<EncodedSegment> segments = new ArrayList<>();

            for (VideoQuality quality : VideoQuality.values()) {
                // In real code: call FFmpeg with these parameters
                String ffmpegArgs = String.format(
                    "ffmpeg -i input.mp4 -b:v %s -hls_time 4 -hls_segment_filename '%s/%%04d.ts' '%s/playlist.m3u8'",
                    BITRATES.get(quality), quality.name().toLowerCase(), quality.name().toLowerCase()
                );
                System.out.println("Encoding [" + quality + "]: " + ffmpegArgs);

                // Simulate 3 segments per quality
                for (int i = 0; i < 3; i++) {
                    String path = String.format("s3://video-cdn/%s/hls/%s/seg_%04d.ts",
                        videoId, quality.name().toLowerCase(), i);
                    segments.add(new EncodedSegment(videoId, quality, i, path));
                }
            }

            System.out.println("Encoding complete: " + segments.size() + " segments for " + videoId);
            return segments;
        }

        // Build HLS master playlist
        public String buildMasterPlaylist(String videoId) {
            return String.format("""
                #EXTM3U
                #EXT-X-VERSION:3

                #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
                https://cdn.example.com/%s/hls/q360p/playlist.m3u8

                #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
                https://cdn.example.com/%s/hls/q720p/playlist.m3u8

                #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
                https://cdn.example.com/%s/hls/q1080p/playlist.m3u8

                #EXT-X-STREAM-INF:BANDWIDTH=15000000,RESOLUTION=3840x2160
                https://cdn.example.com/%s/hls/q4k/playlist.m3u8
                """, videoId, videoId, videoId, videoId);
        }
    }

    // --- View Count Aggregator (Kafka Streams simulation) ---

    static class ViewCountAggregator {
        private final Map<String, AtomicLong> windowCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> totalCounts  = new ConcurrentHashMap<>();
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        public void start() {
            // Flush windowed counts to DB every 30 seconds
            scheduler.scheduleAtFixedRate(this::flushToDb, 30, 30, TimeUnit.SECONDS);
            System.out.println("ViewCountAggregator started (30s flush interval)");
        }

        public void recordView(String videoId) {
            windowCounts.computeIfAbsent(videoId, k -> new AtomicLong(0)).incrementAndGet();
        }

        private void flushToDb() {
            Map<String, Long> batch = new HashMap<>();
            windowCounts.forEach((videoId, counter) -> {
                long delta = counter.getAndSet(0);
                if (delta > 0) {
                    totalCounts.computeIfAbsent(videoId, k -> new AtomicLong(0)).addAndGet(delta);
                    batch.put(videoId, delta);
                }
            });

            if (!batch.isEmpty()) {
                System.out.println("[DB FLUSH] UPDATE view_counts for " + batch.size() + " videos: " + batch);
            }
        }

        public long getViewCount(String videoId) {
            AtomicLong total = totalCounts.get(videoId);
            return total != null ? total.get() : 0;
        }

        public void stop() { scheduler.shutdown(); }
    }

    // --- Demo ---

    public static void main(String[] args) throws Exception {
        System.out.println("=== VIDEO STREAMING SERVICE DEMO ===\n");

        // 1. Upload flow
        ChunkedUploadService uploadService = new ChunkedUploadService();
        String videoId  = UUID.randomUUID().toString();
        String uploadId = uploadService.initUpload(videoId, 5);

        byte[] fakeChunk = new byte[4 * 1024 * 1024]; // 4 MB chunk
        for (int i = 0; i < 5; i++) {
            var result = uploadService.receiveChunk(uploadId, i, fakeChunk);
            System.out.println("  Result: " + result);
        }

        // 2. Encoding
        System.out.println("\n--- Encoding ---");
        VideoEncodingWorker encoder = new VideoEncodingWorker();
        List<EncodedSegment> segments = encoder.encode(videoId, fakeChunk);
        System.out.println("Master playlist:\n" + encoder.buildMasterPlaylist(videoId));

        // 3. View counting
        System.out.println("\n--- View Count ---");
        ViewCountAggregator counter = new ViewCountAggregator();
        counter.start();
        for (int i = 0; i < 1000; i++) counter.recordView(videoId);
        counter.flushToDb(); // manual flush for demo
        System.out.println("View count for video: " + counter.getViewCount(videoId));
        counter.stop();
    }
}
```

---

## 10. Interview Tips

| Area | Key Points |
|------|-----------|
| Upload | Chunked + resumable; never single large HTTP upload |
| Encoding | Async via Kafka; FFmpeg; HLS/DASH adaptive bitrate |
| Storage | S3 for blobs; CDN for delivery; DB only for metadata |
| Scale | CDN is the entire answer for read throughput |
| View counts | Approximate with Kafka streams; don't do DB increment per view |
| ABR | Client-driven quality switching; 4-second segments balance start time vs request count |
| Hot content | Multiple CDN providers + pre-warm edges for viral events |
