# Design Google Drive / Dropbox

## Requirements

**Functional:**
- Upload, download, delete files.
- Sync files across devices.
- Share files/folders.
- File versioning (history).
- Offline support.

**Non-Functional:**
- 500M users, 10M DAU.
- 10 GB average storage per user.
- Total storage: 500M × 10 GB = 5 PB.
- Uploads: 10M DAU × 2 uploads/day = 20M uploads/day ≈ 230/sec.
- High availability, eventual consistency for sync.

---

## Architecture

```
Client App (Desktop/Mobile)
    ↓
Block Sync Protocol (delta sync — only changed blocks)
    ↓
API Gateway
    ↙            ↓             ↘
Upload API   Metadata API    Notification Service
    ↓              ↓
Block Storage   Metadata DB      Message Queue (Sync events)
(S3/GCS)       (MySQL +         (Kafka)
               Sharding)
```

---

## File Upload Design

### Chunking

Large files split into chunks (e.g., 4 MB each).

```
File.pdf (40 MB) → [Block 1][Block 2]...[Block 10]
Each block: SHA-256 hash → unique ID
Deduplication: if hash already exists → skip upload
```

Benefits:
- Resume interrupted uploads (only re-send missing chunks).
- Deduplication reduces storage 30-50%.
- Parallel upload of chunks.

### Upload Flow

```
1. Client: compute block hashes locally.
2. Client → API: "which blocks do I need to upload?" (send hash list)
3. API: compare against block store → return missing block IDs.
4. Client: upload only missing blocks directly to S3 (pre-signed URL).
5. Client: commit file (send block manifest + filename + metadata).
6. API: store file_version record in metadata DB.
7. API: publish FileUpdated event to Kafka.
8. Kafka → notification service: push sync notification to other devices.
```

### Direct Upload to S3

```
Client → GET /upload-url?filename=photo.jpg → { presignedUrl: "https://s3.amazonaws.com/..." }
Client → PUT {presignedUrl}  (uploads directly, bypasses your servers)
Client → POST /files/commit { fileId, blocks[] }
```

---

## Delta Sync (efficient updates)

```
File changes:
  Old: [B1][B2][B3][B4]
  New: [B1][B2'][B3][B5]
  
Only B2' and B5 need uploading.
Metadata stores block manifest per version.
```

---

## Metadata DB Schema

```sql
-- Files
CREATE TABLE files (
    file_id     UUID PRIMARY KEY,
    user_id     BIGINT,
    name        VARCHAR(255),
    parent_id   UUID,           -- null if root
    is_folder   BOOLEAN,
    created_at  TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- File versions
CREATE TABLE file_versions (
    version_id  UUID PRIMARY KEY,
    file_id     UUID,
    block_list  JSON,           -- ordered list of block_ids
    size_bytes  BIGINT,
    created_at  TIMESTAMP,
    created_by  BIGINT
);

-- Blocks
CREATE TABLE blocks (
    block_id    CHAR(64) PRIMARY KEY,   -- SHA-256 hash
    s3_path     VARCHAR(500),
    size        INT
);
```

---

## Sync & Conflict Resolution

**Long polling or WebSocket**: push FileUpdated events to connected clients.

**Conflict resolution** (Dropbox approach):
- If two devices edit the same file offline → keep both.
- Rename conflicted version: `File (User's conflicted copy 2024-01-15).docx`
- Let user merge manually.

**Vector clocks** for more sophisticated conflict detection (used in Dynamo).

---

## Interview Tips

- Chunking + deduplication + delta sync are the key differentiators.
- Pre-signed S3 URLs: server never handles raw file bytes → huge scalability win.
- Sharding metadata DB by user_id.
- File access control: share table linking file_id → shared_with_user_id + permissions.

---

## Project Structure

```
google-drive/
├── src/main/java/com/drive/
│   ├── DriveApplication.java
│   ├── upload/
│   │   ├── UploadController.java       # POST /v1/files/init-upload
│   │   └── ChunkedUploadService.java   # split file → block hashes
│   ├── storage/
│   │   ├── S3StorageService.java       # generate pre-signed PUT URLs
│   │   └── BlockDeduplicationService.java  # SHA-256 → skip dup blocks
│   ├── metadata/
│   │   ├── MetadataController.java     # GET /v1/files/{id}
│   │   ├── MetadataService.java        # CRUD for file/folder records
│   │   └── FileVersionService.java    # track version chain
│   ├── sync/
│   │   ├── SyncController.java         # GET /v1/sync?since=timestamp
│   │   └── ChangelogService.java       # delta changes since last sync
│   └── model/
│       ├── FileMetadata.java           # id, name, ownerId, size, blockIds
│       └── Block.java                  # hash, s3Key, size
└── pom.xml
```

## Core Implementation

```java
// Block: unit of storage — content-addressed by SHA-256
public record Block(String hash, String s3Key, long size) {}

// Split file into fixed-size blocks (4 MB default)
@Service
public class ChunkedUploadService {
    private static final int BLOCK_SIZE = 4 * 1024 * 1024; // 4 MB
    @Autowired BlockRepository blockRepo;
    @Autowired S3StorageService s3;

    public UploadPlan initUpload(String fileName, long fileSize, String ownerId) {
        // Client sends SHA-256 hash of each block — server deduplicates
        int blockCount = (int) Math.ceil((double) fileSize / BLOCK_SIZE);
        List<String> uploadUrls = new ArrayList<>();

        // Return only URLs for blocks we don't already have (dedup)
        // Client sends block hashes in advance via the request body
        return new UploadPlan(fileName, blockCount, BLOCK_SIZE);
    }

    // Called per-block after client hashes each chunk
    public BlockUploadInstruction processBlockHash(String hash, int index) {
        if (blockRepo.existsByHash(hash)) {
            // Block already in S3 — skip upload (deduplication)
            return new BlockUploadInstruction(index, null, true);
        }
        // Generate pre-signed S3 PUT URL — client uploads directly
        String s3Key = "blocks/" + hash;
        String presignedUrl = s3.presignedPutUrl(s3Key, Duration.ofMinutes(15));
        return new BlockUploadInstruction(index, presignedUrl, false);
    }

    // Called after all blocks uploaded — commit file metadata
    public FileMetadata commitUpload(String fileName, List<String> blockHashes, String ownerId) {
        long totalSize = blockHashes.stream()
            .mapToLong(h -> blockRepo.findByHash(h).map(Block::size).orElse(0L))
            .sum();

        FileMetadata meta = new FileMetadata();
        meta.setName(fileName);
        meta.setOwnerId(ownerId);
        meta.setSize(totalSize);
        meta.setBlockHashes(blockHashes);
        meta.setVersion(1);
        meta.setCreatedAt(Instant.now());
        return metadataRepo.save(meta);
    }
}

// S3 pre-signed URLs — server never touches file bytes
@Service
public class S3StorageService {
    @Autowired S3Presigner presigner;
    @Value("${s3.bucket}") String bucket;

    public String presignedPutUrl(String key, Duration ttl) {
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(r -> r.bucket(bucket).key(key))
            .build();
        return presigner.presignPutObject(req).url().toString();
    }

    public String presignedGetUrl(String key, Duration ttl) {
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(r -> r.bucket(bucket).key(key))
            .build();
        return presigner.presignGetObject(req).url().toString();
    }
}

// Sync: return delta changes since last sync timestamp
@Service
public class ChangelogService {
    @Autowired ChangelogRepository repo;

    // Client stores last_sync_timestamp; polls /v1/sync?since=T
    public List<ChangeEvent> getChangesSince(String userId, Instant since) {
        return repo.findByUserIdAndTimestampAfterOrderByTimestampAsc(userId, since);
    }

    public void recordChange(String userId, String fileId, String changeType) {
        repo.save(new ChangeEvent(userId, fileId, changeType, Instant.now()));
    }
}
```
