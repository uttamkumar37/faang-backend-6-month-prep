# Design a Payment System

## Requirements

**Functional:**
- Process payments (charge card/wallet).
- Transfer money between accounts.
- Refunds.
- Payment history.

**Non-Functional:**
- Exactly-once payment processing (no double-charge).
- Consistent state (account balances always accurate).
- Audit trail (every state change logged).
- P99 latency < 2 seconds.
- Compliance: PCI DSS for card data.

---

## Core Challenge: Exactly-Once Processing

### Idempotency Keys

Client generates a UUID; server stores it. Replaying the same request returns cached result.

```
POST /v1/payments
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
{
  "from_account": "ACC-001",
  "to_account": "ACC-002",
  "amount": 100.00,
  "currency": "USD"
}
```

```sql
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    response_code   INT,
    response_body   JSON,
    created_at      TIMESTAMP DEFAULT NOW(),
    expires_at      TIMESTAMP
);
-- TTL: 24 hours
```

### Transactional Outbox Pattern

Prevents the dual-write problem (DB write + message publish atomically).

```
BEGIN TRANSACTION;
  UPDATE accounts SET balance = balance - 100 WHERE id = 'ACC-001';
  UPDATE accounts SET balance = balance + 100 WHERE id = 'ACC-002';
  INSERT INTO outbox (event_type, payload) VALUES ('PaymentCompleted', {...});
COMMIT;

Background poller: read from outbox → publish to Kafka → mark as published
```

---

## Double-Entry Bookkeeping

Every cent must be tracked. Inspired by accounting.

```
Each payment = two journal entries:
  Debit  ACC-001   -$100.00
  Credit ACC-002   +$100.00

Balance = SUM of all debit/credit entries (never updated in place)
```

```sql
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    payment_id      UUID NOT NULL,
    account_id      UUID NOT NULL,
    amount          DECIMAL(18,2),   -- positive = credit, negative = debit
    entry_type      VARCHAR(10),     -- DEBIT or CREDIT
    created_at      TIMESTAMP
);

-- Balance query:
SELECT SUM(amount) AS balance FROM ledger_entries WHERE account_id = ?
```

---

## Payment State Machine

```
CREATED → PROCESSING → COMPLETED
              ↓              ↓
           FAILED        REFUND_PENDING → REFUNDED
```

Each state stored with timestamp. Strict transitions (no backward moves).

---

## Architecture

```
Client → Payment API → [Idempotency Check] → Payment Processor
                              ↓
                         Ledger Service
                              ↓
              ┌──────────────┬──────────────┐
           Accounts DB    Ledger DB     Outbox Table
           (Postgres)   (append-only)   (Postgres)
                              ↓
                          Kafka Consumer
                              ↓
                    ┌─────────────────┐
               Notification        Analytics
               Service              (ClickHouse)
```

---

## External PSP Integration

```java
// Wrap PSP call with idempotency key + retry
PaymentResult charge(ChargeRequest request) {
    String pspIdempotencyKey = "psp:" + request.getIdempotencyKey();
    try {
        return stripe.charge(request.toStripeParams().idempotencyKey(pspIdempotencyKey));
    } catch (StripeException e) {
        // Query PSP to reconcile on network failure — don't retry blindly
        return stripe.retrieve(pspIdempotencyKey);
    }
}
```

---

## Reconciliation

Nightly job: compare internal ledger with PSP settlement reports.  
Flag discrepancies for human review.

---

## Security

- **PCI DSS**: never store raw card numbers; use PSP tokenization.
- **Encryption**: TLS 1.3 in transit; AES-256 at rest for sensitive fields.
- **Fraud detection**: ML model scores each transaction; block if score > threshold.
- **Audit logging**: immutable append-only audit table with who/what/when.

---

## Interview Tips

- Idempotency + transactional outbox are the two most important concepts.
- Double-entry bookkeeping shows deep knowledge — mention it.
- Exactly-once is harder than at-least-once; explain the distributed systems challenge.
- Compliance is a real constraint — mention PCI DSS, tokenization.

---

## Project Structure

```
payment-system/
├── src/main/java/com/payment/
│   ├── PaymentApplication.java
│   ├── api/
│   │   └── PaymentController.java        # POST /v1/payments
│   ├── service/
│   │   ├── PaymentService.java           # idempotency + ledger + outbox
│   │   ├── IdempotencyService.java       # store/check idempotency keys
│   │   └── FraudCheckService.java        # pre-auth risk scoring
│   ├── ledger/
│   │   └── LedgerService.java            # double-entry bookkeeping
│   ├── outbox/
│   │   ├── OutboxRepository.java         # transactional outbox table
│   │   └── OutboxRelay.java              # polling relay → Kafka
│   ├── saga/
│   │   ├── PaymentSagaOrchestrator.java  # orchestrate reserve→charge→confirm
│   │   └── CompensationService.java      # rollback on failure
│   └── model/
│       ├── Payment.java                  # id, fromAccount, toAccount, amount, status
│       └── LedgerEntry.java              # debit/credit double-entry
└── pom.xml
```

## Core Implementation

```java
// Payment service — idempotent, exactly-once via DB lock + outbox
@Service
@Transactional
public class PaymentService {
    @Autowired IdempotencyService idempotency;
    @Autowired LedgerService ledger;
    @Autowired OutboxRepository outbox;
    @Autowired FraudCheckService fraud;

    public PaymentResult processPayment(PaymentRequest req, String idempotencyKey) {
        // 1. Check idempotency — return cached result if already processed
        Optional<PaymentResult> existing = idempotency.getResult(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        // 2. Fraud check (read-only, outside transaction)
        if (fraud.isHighRisk(req)) throw new FraudException("Blocked by risk engine");

        try {
            // 3. Debit source account (SELECT FOR UPDATE prevents concurrent debit)
            ledger.debit(req.fromAccount(), req.amount(), req.currency());

            // 4. Credit destination account
            ledger.credit(req.toAccount(), req.amount(), req.currency());

            // 5. Save payment record
            Payment payment = new Payment(req, PaymentStatus.COMPLETED);
            paymentRepo.save(payment);

            // 6. Write outbox event IN SAME TRANSACTION — atomic with ledger changes
            outbox.save(new OutboxEvent("PaymentCompleted",
                Map.of("paymentId", payment.getId(), "amount", req.amount())));

            // 7. Store idempotency result
            PaymentResult result = new PaymentResult(payment.getId(), "COMPLETED");
            idempotency.storeResult(idempotencyKey, result);
            return result;

        } catch (InsufficientFundsException e) {
            idempotency.storeResult(idempotencyKey, new PaymentResult(null, "INSUFFICIENT_FUNDS"));
            throw e;
        }
        // Transaction commits everything (ledger + outbox) or rolls back atomically
    }
}

// Double-entry ledger — every payment = debit + credit
@Service
public class LedgerService {
    @Autowired LedgerRepository repo;
    @Autowired AccountRepository accounts;

    public void debit(String accountId, BigDecimal amount, String currency) {
        Account acc = accounts.findForUpdate(accountId); // SELECT FOR UPDATE
        if (acc.getBalance().compareTo(amount) < 0)
            throw new InsufficientFundsException(accountId);
        acc.setBalance(acc.getBalance().subtract(amount));
        accounts.save(acc);
        repo.save(new LedgerEntry(accountId, "DEBIT", amount, currency, Instant.now()));
    }

    public void credit(String accountId, BigDecimal amount, String currency) {
        Account acc = accounts.findForUpdate(accountId);
        acc.setBalance(acc.getBalance().add(amount));
        accounts.save(acc);
        repo.save(new LedgerEntry(accountId, "CREDIT", amount, currency, Instant.now()));
    }
}

// Outbox relay — polls outbox table, publishes to Kafka, marks as published
@Component
public class OutboxRelay {
    @Autowired OutboxRepository outbox;
    @Autowired KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 500) // every 500ms
    public void relay() {
        List<OutboxEvent> pending = outbox.findPendingBatch(100);
        for (OutboxEvent e : pending) {
            try {
                kafka.send("payments", e.getAggregateId(), e.getPayload()).get();
                e.setPublished(true);
                outbox.save(e);
            } catch (Exception ex) {
                log.error("Outbox relay failed for event {}: {}", e.getId(), ex.getMessage());
            }
        }
    }
}

// Idempotency service — prevents double-charge on retry
@Service
public class IdempotencyService {
    @Autowired IdempotencyRepository repo;

    public Optional<PaymentResult> getResult(String key) {
        return repo.findByKey(key).map(IdempotencyRecord::getResult);
    }

    public void storeResult(String key, PaymentResult result) {
        // TTL = 24 hours (client should not retry after 24h)
        repo.save(new IdempotencyRecord(key, result, Instant.now().plus(24, ChronoUnit.HOURS)));
    }
}
```
