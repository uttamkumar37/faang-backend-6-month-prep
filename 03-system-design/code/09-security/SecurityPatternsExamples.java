package systemdesign.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Security Patterns for Backend Systems
 *
 * Covers:
 * 1. JWT creation + validation (RS256)
 * 2. Password hashing (PBKDF2 — bcrypt/Argon2id would use external libs)
 * 3. SQL injection prevention (parameterized queries demo)
 * 4. SSRF URL validator
 * 5. Rate limiter for login endpoint
 * 6. CSRF token generation + validation
 * 7. Secure random token generation
 * 8. Input sanitization / XSS prevention
 */
public class SecurityPatternsExamples {

    // =========================================================================
    // 1. Secure Random Token Generator
    // =========================================================================

    static class SecureTokenGenerator {
        private static final SecureRandom SECURE_RANDOM;

        static {
            try {
                SECURE_RANDOM = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Strong SecureRandom unavailable", e);
            }
        }

        /** Generate cryptographically secure URL-safe token of given byte length */
        public static String generateToken(int byteLength) {
            byte[] bytes = new byte[byteLength];
            SECURE_RANDOM.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        /** Generate 256-bit (32 byte) refresh token */
        public static String refreshToken() {
            return generateToken(32);
        }

        /** Generate CSRF token */
        public static String csrfToken() {
            return generateToken(24);
        }
    }

    // =========================================================================
    // 2. Password Hashing (PBKDF2 — production uses bcrypt or Argon2id)
    // =========================================================================

    static class PasswordHasher {
        private static final int ITERATIONS   = 600_000; // NIST 2023 recommendation
        private static final int KEY_LENGTH   = 256;     // bits
        private static final int SALT_LENGTH  = 16;      // bytes
        private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

        public String hash(String plainPassword) {
            byte[] salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf2(plainPassword, salt);
            return Base64.getEncoder().encodeToString(salt) + ":" +
                   Base64.getEncoder().encodeToString(hash);
        }

        public boolean verify(String plainPassword, String storedHash) {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual = pbkdf2(plainPassword, salt);
            return MessageDigest.isEqual(actual, expected); // constant-time compare
        }

        private byte[] pbkdf2(String password, byte[] salt) {
            try {
                PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
                SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
                return skf.generateSecret(spec).getEncoded();
            } catch (Exception e) {
                throw new RuntimeException("Password hashing failed", e);
            }
        }
    }

    // =========================================================================
    // 3. JWT (simplified — production: use java-jwt or nimbus-jose-jwt)
    // =========================================================================

    static class SimpleJwt {

        /** Sign data with HMAC-SHA256 (HS256) */
        static String sign(String header, String payload, String secret) throws Exception {
            String data = b64(header.getBytes()) + "." + b64(payload.getBytes());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return data + "." + sig;
        }

        /** Verify and parse token. Returns payload JSON or throws if invalid. */
        static String verify(String token, String secret) throws Exception {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new SecurityException("Invalid token format");

            String data = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

            if (!MessageDigest.isEqual(expectedSig.getBytes(), parts[2].getBytes())) {
                throw new SecurityException("Invalid token signature");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            // Check expiry
            Pattern expPattern = Pattern.compile("\"exp\":(\\d+)");
            Matcher m = expPattern.matcher(payload);
            if (m.find()) {
                long exp = Long.parseLong(m.group(1));
                if (System.currentTimeMillis() / 1000 > exp) {
                    throw new SecurityException("Token expired");
                }
            }
            return payload;
        }

        static String b64(byte[] data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        }

        static String createAccessToken(String userId, List<String> roles, String secret) throws Exception {
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long now = System.currentTimeMillis() / 1000;
            String payload = String.format(
                "{\"sub\":\"%s\",\"roles\":%s,\"iat\":%d,\"exp\":%d}",
                userId, roles, now, now + 900); // 15 min
            return sign(header, payload, secret);
        }
    }

    // =========================================================================
    // 4. SSRF URL Validator
    // =========================================================================

    static class SsrfValidator {
        // Private/reserved IP ranges to block
        private static final List<long[]> BLOCKED_RANGES = List.of(
            new long[]{ipToLong("10.0.0.0"),     ipToLong("10.255.255.255")},
            new long[]{ipToLong("172.16.0.0"),   ipToLong("172.31.255.255")},
            new long[]{ipToLong("192.168.0.0"),  ipToLong("192.168.255.255")},
            new long[]{ipToLong("127.0.0.0"),    ipToLong("127.255.255.255")},
            new long[]{ipToLong("169.254.0.0"),  ipToLong("169.254.255.255")}, // link-local/AWS metadata
            new long[]{ipToLong("0.0.0.0"),      ipToLong("0.255.255.255")}
        );

        private final Set<String> allowedDomains; // allowlist

        SsrfValidator(Set<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        public boolean isSafe(String urlStr) {
            try {
                java.net.URL url = new java.net.URL(urlStr);

                // Must be http or https
                String scheme = url.getProtocol().toLowerCase();
                if (!scheme.equals("http") && !scheme.equals("https")) return false;

                String host = url.getHost().toLowerCase();

                // Check allowlist if configured
                if (!allowedDomains.isEmpty() && !allowedDomains.contains(host)) {
                    System.out.println("SSRF block: host not in allowlist: " + host);
                    return false;
                }

                // Resolve DNS and check resulting IP
                java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
                for (java.net.InetAddress addr : addrs) {
                    long ip = ipToLong(addr.getHostAddress());
                    for (long[] range : BLOCKED_RANGES) {
                        if (ip >= range[0] && ip <= range[1]) {
                            System.out.println("SSRF block: IP in blocked range: " + addr.getHostAddress());
                            return false;
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                System.out.println("SSRF validation error: " + e.getMessage());
                return false;
            }
        }

        private static long ipToLong(String ip) {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return 0;
            long result = 0;
            for (String part : parts) {
                result = result * 256 + Long.parseLong(part);
            }
            return result;
        }
    }

    // =========================================================================
    // 5. Login Rate Limiter + Account Lockout
    // =========================================================================

    static class LoginRateLimiter {
        private final int maxAttempts;
        private final long windowMs;
        // ip/username → list of attempt timestamps
        private final Map<String, List<Long>> attempts = new ConcurrentHashMap<>();
        private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

        LoginRateLimiter(int maxAttempts, long windowMs) {
            this.maxAttempts = maxAttempts;
            this.windowMs    = windowMs;
        }

        public LoginResult tryLogin(String clientKey, boolean credentialsValid) {
            long now = System.currentTimeMillis();

            // Check if account is locked
            Long lockExpiry = lockedUntil.get(clientKey);
            if (lockExpiry != null && now < lockExpiry) {
                long secsLeft = (lockExpiry - now) / 1000;
                return LoginResult.locked("Account locked for " + secsLeft + " more seconds");
            }

            // Clean old attempts outside window
            List<Long> history = attempts.computeIfAbsent(clientKey, k -> new ArrayList<>());
            synchronized (history) {
                history.removeIf(ts -> now - ts > windowMs);
            }

            // Check rate limit before processing
            synchronized (history) {
                if (history.size() >= maxAttempts) {
                    // Lock account
                    lockedUntil.put(clientKey, now + 15 * 60 * 1000); // 15 min lockout
                    history.clear();
                    return LoginResult.locked("Too many failed attempts. Account locked for 15 minutes.");
                }
            }

            if (!credentialsValid) {
                synchronized (history) { history.add(now); }
                int remaining = maxAttempts - history.size();
                return LoginResult.failed("Invalid credentials. " + remaining + " attempts remaining.");
            }

            // Success: clear history
            attempts.remove(clientKey);
            return LoginResult.success();
        }

        record LoginResult(boolean success, boolean locked, String message) {
            static LoginResult success()           { return new LoginResult(true,  false, "OK"); }
            static LoginResult failed(String msg)  { return new LoginResult(false, false, msg); }
            static LoginResult locked(String msg)  { return new LoginResult(false, true,  msg); }
        }
    }

    // =========================================================================
    // 6. CSRF Token Store
    // =========================================================================

    static class CsrfTokenStore {
        private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

        public String issueToken(String sessionId) {
            String token = SecureTokenGenerator.csrfToken();
            sessionTokens.put(sessionId, token);
            return token;
        }

        public boolean validate(String sessionId, String submittedToken) {
            String expected = sessionTokens.get(sessionId);
            if (expected == null || submittedToken == null) return false;
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submittedToken.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    // =========================================================================
    // 7. Input Sanitizer (XSS prevention — basic HTML encoding)
    // =========================================================================

    static class InputSanitizer {
        public String htmlEncode(String input) {
            if (input == null) return "";
            return input
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&#x27;");
        }

        // Validate URL is safe to render in href (prevent javascript: injection)
        public boolean isSafeUrl(String url) {
            if (url == null) return false;
            String lower = url.trim().toLowerCase();
            return lower.startsWith("http://") || lower.startsWith("https://") ||
                   lower.startsWith("/");
        }

        // Strip all HTML tags (for plain-text contexts)
        public String stripHtml(String html) {
            return html == null ? "" : html.replaceAll("<[^>]*>", "");
        }
    }

    // =========================================================================
    // Main Demo
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== SECURITY PATTERNS DEMO ===\n");

        // 1. Token generation
        System.out.println("--- Secure Tokens ---");
        System.out.println("Refresh token: " + SecureTokenGenerator.refreshToken());
        System.out.println("CSRF token:    " + SecureTokenGenerator.csrfToken());

        // 2. Password hashing
        System.out.println("\n--- Password Hashing (PBKDF2) ---");
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("MyS3cur3P@ss!");
        System.out.println("Hash: " + hash.substring(0, 30) + "...");
        System.out.println("Verify correct:   " + hasher.verify("MyS3cur3P@ss!", hash));
        System.out.println("Verify incorrect: " + hasher.verify("wrongpassword", hash));

        // 3. JWT
        System.out.println("\n--- JWT ---");
        String secret = "super-secret-signing-key-for-demo";
        String token = SimpleJwt.createAccessToken("user123", List.of("USER", "MANAGER"), secret);
        System.out.println("Token: " + token.substring(0, 40) + "...");
        String payload = SimpleJwt.verify(token, secret);
        System.out.println("Payload: " + payload);

        // 4. SSRF validation
        System.out.println("\n--- SSRF Validator ---");
        SsrfValidator ssrfValidator = new SsrfValidator(Set.of("api.github.com", "s3.amazonaws.com"));
        System.out.println("api.github.com safe:          " + ssrfValidator.isSafe("https://api.github.com/repos"));
        System.out.println("internal 10.0.0.1 safe:       " + ssrfValidator.isSafe("http://10.0.0.1/admin"));
        System.out.println("AWS metadata 169.254... safe: " + ssrfValidator.isSafe("http://169.254.169.254/latest/meta-data/"));
        System.out.println("not-in-allowlist safe:        " + ssrfValidator.isSafe("https://evil.com/steal"));

        // 5. Login rate limiter
        System.out.println("\n--- Login Rate Limiter ---");
        LoginRateLimiter limiter = new LoginRateLimiter(5, 15 * 60 * 1000);
        String clientKey = "192.168.1.1:alice@example.com";
        for (int i = 1; i <= 6; i++) {
            LoginRateLimiter.LoginResult result = limiter.tryLogin(clientKey, false);
            System.out.println("Attempt " + i + ": " + result);
        }

        // 6. CSRF
        System.out.println("\n--- CSRF Tokens ---");
        CsrfTokenStore csrfStore = new CsrfTokenStore();
        String sessionId = "sess-abc123";
        String csrfToken = csrfStore.issueToken(sessionId);
        System.out.println("Valid CSRF:   " + csrfStore.validate(sessionId, csrfToken));
        System.out.println("Invalid CSRF: " + csrfStore.validate(sessionId, "tampered-token"));

        // 7. XSS prevention
        System.out.println("\n--- Input Sanitization ---");
        InputSanitizer sanitizer = new InputSanitizer();
        String xssPayload = "<script>alert('XSS')</script> Hello & World";
        System.out.println("Raw:      " + xssPayload);
        System.out.println("Encoded:  " + sanitizer.htmlEncode(xssPayload));
        System.out.println("Safe URL: " + sanitizer.isSafeUrl("javascript:alert(1)"));
        System.out.println("Safe URL: " + sanitizer.isSafeUrl("https://example.com"));
    }
}
