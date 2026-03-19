# Database & JPA

## 1. SQL Fundamentals for Backend Engineers

### Window Functions (crucial for analytics)

```sql
-- Running total per customer
SELECT
    order_id,
    customer_id,
    amount,
    SUM(amount) OVER (PARTITION BY customer_id ORDER BY created_at) AS running_total,
    RANK() OVER (PARTITION BY customer_id ORDER BY amount DESC) AS rank_by_amount,
    LAG(amount, 1) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_amount
FROM orders;
```

### CTEs (Common Table Expressions)

```sql
WITH monthly_revenue AS (
    SELECT
        DATE_TRUNC('month', created_at) AS month,
        SUM(total) AS revenue
    FROM orders
    WHERE status = 'COMPLETED'
    GROUP BY 1
),
growth AS (
    SELECT
        month,
        revenue,
        LAG(revenue) OVER (ORDER BY month) AS prev_revenue
    FROM monthly_revenue
)
SELECT
    month,
    revenue,
    ROUND((revenue - prev_revenue) / prev_revenue * 100, 2) AS growth_pct
FROM growth;
```

### EXPLAIN ANALYZE (query plan interpretation)

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM orders
WHERE customer_id = '123e4567-...'
  AND created_at > NOW() - INTERVAL '30 days';
```

Look for:
- `Seq Scan` on large tables → missing index.
- `Nested Loop` with large outer side → performance may degrade O(n²).
- `Buffers: shared hit` vs `read` ratio → cache hit rate.
- Actual vs estimated rows → stale statistics (run `ANALYZE`).

---

## 2. Indexing Strategy

### Single-column index

```sql
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
```

### Composite index (column order matters!)

```sql
-- Query: WHERE customer_id = ? AND status = ? ORDER BY created_at DESC
-- Index should match: column order of equality predicates first, then range/sort
CREATE INDEX idx_orders_customer_status_created 
    ON orders(customer_id, status, created_at DESC);
```

The **leftmost prefix rule**: a composite index on (a, b, c) is used for:
- `WHERE a = ?`
- `WHERE a = ? AND b = ?`
- `WHERE a = ? AND b = ? AND c = ?`
- `ORDER BY a` (if no WHERE)

But NOT for: `WHERE b = ?` or `WHERE c = ?` alone.

### Partial index (selective)

```sql
-- Only index unprocessed events — smaller, faster
CREATE INDEX idx_events_unprocessed 
    ON domain_events(created_at) 
    WHERE processed = false;
```

### Covering index (index-only scan)

```sql
-- Query: SELECT id, email FROM users WHERE username = ?
-- Make index cover all columns in SELECT + WHERE — no table heap access needed
CREATE INDEX idx_users_username_covering 
    ON users(username) INCLUDE (id, email);
```

---

## 3. JPA/Hibernate

### Entity basics

```java
@Entity
@Table(name = "orders",
       indexes = @Index(name = "idx_orders_customer", columnList = "customer_id"))
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @OneToMany(mappedBy = "order", 
               cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version
    private Long version; // optimistic locking

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
```

### N+1 Query Problem

```java
// BAD — N+1 queries
List<Order> orders = orderRepo.findAll();     // 1 query
for (Order o : orders) {
    System.out.println(o.getItems().size());  // N lazy queries
}

// FIX 1: JOIN FETCH in JPQL
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.customerId = :id")
List<Order> findWithItems(@Param("id") UUID customerId);

// FIX 2: EntityGraph
@EntityGraph(attributePaths = {"items", "items.product"})
List<Order> findByCustomerId(UUID customerId);

// FIX 3: Batch fetching (in-clause)
@BatchSize(size = 20) // load items in batches of 20 orders at a time
@OneToMany(mappedBy = "order")
private List<OrderItem> items;
```

### Optimistic Locking

```java
@Version
private Long version;

// JPA generates: UPDATE orders SET ..., version = 6 WHERE id = ? AND version = 5
// If another transaction updated it first (version now 6), throws:
// OptimisticLockException → handle with retry or show "someone else changed this"
```

### Pessimistic Locking

```java
@Query("SELECT o FROM Order o WHERE o.id = :id")
@Lock(LockModeType.PESSIMISTIC_WRITE) // SELECT ... FOR UPDATE
Optional<Order> findByIdForUpdate(@Param("id") UUID id);
```

Use pessimistic lock for inventory reservation (prevent oversell), financial transfers.

---

## 4. Database Design Patterns

### Soft Delete

```sql
ALTER TABLE orders ADD COLUMN deleted_at TIMESTAMP;
-- Never DELETE, just SET deleted_at = NOW()

-- Hide from all queries with @Where (Hibernate)
@Where(clause = "deleted_at IS NULL")
@Entity public class Order { ... }
```

### Audit Trail

```java
@EntityListeners(AuditingEntityListener.class)
@Entity
public class Order {
    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

### Table Partitioning (for large tables)

```sql
-- Range partition orders by year
CREATE TABLE orders (
    id UUID,
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);

CREATE TABLE orders_2023 PARTITION OF orders
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');
CREATE TABLE orders_2024 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
```

---

## 5. Connection Pool (HikariCP)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # max DB connections
      minimum-idle: 5
      connection-timeout: 3000    # ms — fail fast rather than queue
      idle-timeout: 600000        # 10 min
      max-lifetime: 1800000       # 30 min
      validation-timeout: 5000
      connection-test-query: SELECT 1
```

**Pool sizing formula** (rough):
```
pool size = (core count × 2) + number of spindle disks

For PostgreSQL with SSDs and moderate concurrency:
pool size ≈ 10–20 per application instance
```

Too large a pool causes **connection thrashing** on the DB side — each connection uses RAM and CPU context.

---

## 6. Interview Q&A

**Q: What is the difference between optimistic and pessimistic locking?**  
Optimistic locking assumes conflicts are rare. No lock is held — a version column is checked at update time. If the version doesn't match what was read, the update fails with `OptimisticLockException` and the caller can retry. High concurrency, low contention. Pessimistic locking acquires a database row lock (`SELECT FOR UPDATE`), preventing other transactions from reading or writing that row. Guaranteed no conflicts but reduces concurrency. Use optimistic for read-mostly data (user profiles), pessimistic for high-contention critical sections (inventory, account balance).

**Q: Explain the left-most prefix rule for composite indexes.**  
A composite index on (a, b, c) is a sorted structure ordered by a first, then b within equal a values, then c within equal (a, b) values. This means the index can only be efficiently used if the query filters on a leading prefix of the index columns. A query on `b` alone can't use this index because the b values are interleaved across different a values — the DB would have to scan the entire index. A query on `a = ? AND b = ?` can use it efficiently by scanning only the (a,b) sub-tree.

**Q: How do you diagnose a slow query in production?**  
1. Enable slow query logging (`log_min_duration_statement = 1000` in PostgreSQL). 2. Identify the query from logs. 3. Run `EXPLAIN (ANALYZE, BUFFERS)` on it in a staging environment (never analyze in prod without caution — ANALYZE actually executes the query). 4. Look for sequential scans on large tables, large row estimates vs actuals (stale statistics), high buffer reads. 5. Add appropriate index, run `ANALYZE table_name` to update statistics, verify plan improves. 6. Test in staging with production-size data if possible.
