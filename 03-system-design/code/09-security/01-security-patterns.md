# Security Patterns for Backend Systems

> FAANG-level security patterns: Auth, Authorization, common attack prevention.

---

## 1. Authentication Patterns

### 1.1 JWT (JSON Web Token)
```
Structure:  Header.Payload.Signature
Header:     {"alg":"RS256","typ":"JWT"}
Payload:    {"sub":"user123","iat":1700000000,"exp":1700003600,"roles":["USER"]}
Signature:  RSA_SHA256(base64(header) + "." + base64(payload), privateKey)
```

**Key rules:**
- Use **asymmetric signing** (RS256/ES256) for distributed systems — verifiers need public key only
- Use **symmetric signing** (HS256) only for single-service tokens
- Set short expiry (15 min) + refresh tokens (7 days, stored in DB for revocation)
- Store refresh tokens server-side to enable logout/revocation
- Access tokens: stateless (verify with public key, no DB hit)
- Refresh tokens: stateful (DB lookup required — allows invalidation)

### 1.2 OAuth2 / OIDC Flows
```
Authorization Code (web apps):
  User → Client → /authorize → Auth Server
  Auth Server → callback?code=XYZ → Client
  Client → /token (code + client_secret) → Auth Server → access_token + refresh_token

Client Credentials (service-to-service):
  Service → /token (client_id + client_secret) → Auth Server → access_token

PKCE (mobile/SPA — no client secret):
  Client generates code_verifier + code_challenge (SHA-256)
  Sends code_challenge in /authorize
  Sends code_verifier in /token (server verifies hash matches)
```

### 1.3 Session-Based Auth (traditional)
```
POST /login → Server creates session → stores in Redis with TTL
Set-Cookie: sessionId=<random_id>; HttpOnly; Secure; SameSite=Strict

On each request:
  Server reads sessionId from cookie
  Looks up session in Redis → validates
  Invalidate: DELETE session from Redis
```

---

## 2. Authorization Patterns

### 2.1 RBAC (Role-Based Access Control)
```
Roles: ADMIN, MANAGER, USER, READONLY

Role → Permissions mapping:
  ADMIN:    create, read, update, delete
  MANAGER:  create, read, update
  USER:     create, read (own resources)
  READONLY: read

Check: user.role has permission for action on resource
```

### 2.2 ABAC (Attribute-Based Access Control)
- More flexible than RBAC — policy evaluates attributes
- e.g., "User can edit document if user.id == document.ownerId OR user.team == document.team"
- Used in: AWS IAM policies, Google Cloud IAM

### 2.3 JWT + RBAC in API Gateway
```
Request → API Gateway
  → Validate JWT signature (public key or JWKS endpoint)
  → Extract roles from claims
  → Enforce route-level policy
  → Forward to service (with X-User-Id, X-Roles headers stripped from client)
```

---

## 3. OWASP Top 10 Defenses

### A01: Broken Access Control
- Check authorization on EVERY request, not just UI navigation
- Use deny-by-default: anything not explicitly allowed is denied
- Never trust client-provided user IDs — use authenticated identity from token
- Enforce object-level permissions: can this user access THIS specific record?

### A02: Cryptographic Failures
```
DO:                                   DON'T:
AES-256-GCM for encryption           AES-ECB (reveals patterns)
bcrypt/Argon2id for passwords         MD5/SHA-1 for passwords
TLS 1.2–1.3 for transit              Store plain-text passwords
HTTPS-only with HSTS                 Self-signed certs in production
Rotate secrets, use HSM/Vault        Hardcode secrets in code
```

### A03: Injection (SQL, LDAP, Command)
```java
// SQL INJECTION — NEVER DO THIS:
String query = "SELECT * FROM users WHERE id = " + userId;

// ALWAYS USE PARAMETERIZED QUERIES:
PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
stmt.setString(1, userId);

// JPA — use named parameters:
entityManager.createQuery("FROM User WHERE id = :id")
    .setParameter("id", userId).getResultList();
```

### A07: Identification & Auth Failures
- Enforce MFA for admin accounts
- Limit failed login attempts (rate limit + lockout)
- Rotate JWT signing keys periodically
- Use secure random for tokens: `SecureRandom.getInstanceStrong()`
- Validate token issuer, audience, and expiry on every request

### A08: Software Integrity Failures (Supply Chain)
- Pin dependency versions; use lockfiles
- Verify SHA checksums of downloaded artifacts
- Scan with SNYK / OWASP Dependency-Check in CI pipeline
- Container image scanning before deployment

---

## 4. XSS Prevention
```
Type 1 - Reflected: attacker injects script via URL param → server echoes it
Type 2 - Stored: attacker stores script in DB → served to other users
Type 3 - DOM-based: client JS reads attacker-controlled data, inserts into DOM

Defenses:
  - Backend: NEVER render user input as raw HTML
  - Backend: HTML-encode all user-supplied strings in responses
  - Content-Security-Policy: default-src 'self'; script-src 'self'
  - HttpOnly cookies: JS can't read session cookie even if XSS succeeds
  - Frontend: use textContent not innerHTML when inserting user data
```

---

## 5. CSRF Prevention
```
Attack: Malicious site tricks authenticated user's browser into making a request.

Defense 1 — CSRF Token (stateful):
  Server issues random CSRF token → stored in session
  Each form includes a hidden field with the token
  Server validates token on state-changing requests (POST/PUT/DELETE)

Defense 2 — SameSite Cookie (modern):
  Set-Cookie: session=...; SameSite=Strict
  Browser won't send cookie in cross-site requests

Defense 3 — Custom Header Check:
  API: all POST requests must include X-Requested-With: XMLHttpRequest
  Browser's same-origin policy prevents cross-site JS from setting custom headers
```

---

## 6. SSRF Prevention
```
Attack: Attacker tricks backend to make HTTP requests to internal services.
  e.g., POST {"imageUrl": "http://169.254.169.254/latest/meta-data/"} → AWS metadata

Defenses:
  - Allowlist: only permit fetching from approved external domains
  - Block internal IP ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16
  - Resolve DNS at validation time, then compare against blocklist
  - Never pass raw user-supplied URLs directly to HTTP clients
  - Use network-level egress filtering as defense-in-depth
```

---

## 7. Secrets Management
```
DO:
  - Store secrets in Vault (HashiCorp), AWS Secrets Manager, or GCP Secret Manager
  - Inject secrets at runtime via environment variables or SDK calls
  - Rotate secrets automatically (database passwords, API keys)
  - Audit all secret access

DON'T:
  - Commit secrets to Git (use pre-commit hooks + tools like TruffleHog)
  - Store in config files that go to version control
  - Log secrets (redact in log formatters)
  - Use the same secret for multiple environments
```

---

## 8. API Security Checklist

```
Authentication:
  ☐ All endpoints require authentication (except explicitly public ones)
  ☐ JWT validated: signature, expiry, issuer, audience
  ☐ Refresh tokens stored server-side and can be revoked

Authorization:
  ☐ Object-level: user can only access their own data
  ☐ Function-level: user can only perform actions their role allows
  ☐ Admin endpoints extra-protected (separate auth, IP allowlist)

Transport:
  ☐ TLS 1.2+ enforced; HTTP redirects to HTTPS
  ☐ HSTS header set (Strict-Transport-Security: max-age=31536000)
  ☐ Certificate pinning for mobile clients

Input:
  ☐ All user input validated and sanitized server-side
  ☐ Parameterized queries / ORM everywhere
  ☐ File uploads: type checked, virus scanned, stored outside web root

Rate Limiting:
  ☐ Login endpoint: max 5 attempts per 15 min per IP
  ☐ API endpoints: per-user and per-IP rate limits
  ☐ Separate limits for admin vs regular user calls

Headers:
  ☐ X-Content-Type-Options: nosniff
  ☐ X-Frame-Options: DENY
  ☐ Content-Security-Policy configured
  ☐ Server header suppressed (don't leak tech stack)

Logging:
  ☐ Log all auth events (login, logout, failures)
  ☐ Log all admin actions
  ☐ Never log passwords, tokens, or PII in plain text
  ☐ Centralized, tamper-resistant log storage
```
