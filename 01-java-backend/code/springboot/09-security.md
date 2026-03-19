# Security for Backend Engineers

## 1. Spring Security Architecture

```
HTTP Request
    ↓
FilterChainProxy (Spring Security's entry point)
    ↓
SecurityFilterChain (your configuration)
    ├── CorsFilter
    ├── CsrfFilter
    ├── JwtAuthenticationFilter (custom)
    ├── UsernamePasswordAuthenticationFilter
    ├── ExceptionTranslationFilter
    └── FilterSecurityInterceptor (authorization)
    ↓
DispatcherServlet → Controller
```

### Minimal JWT security config (Spring Boot 3)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)      // stateless REST — no CSRF needed
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403))
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost factor 12
    }
}
```

---

## 2. JWT (JSON Web Token)

### Structure

```
header.payload.signature
  ↓        ↓           ↓
base64  base64      HMAC-SHA256 or RS256
```

```json
// Header
{ "alg": "RS256", "typ": "JWT" }

// Payload
{
  "sub": "user-uuid-123",
  "roles": ["USER"],
  "iat": 1698912000,
  "exp": 1698915600,    // 1 hour expiry
  "jti": "unique-token-id"  // for revocation
}
```

### JWT filter

```java
@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parse(token);  // throws if expired/invalid
            String userId = claims.getSubject();
            List<String> roles = claims.get("roles", List.class);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                roles.stream().map(SimpleGrantedAuthority::new).collect(toList())
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

### RS256 vs HS256

| | HS256 | RS256 |
|---|---|---|
| Algorithm | HMAC-SHA256 | RSA |
| Key | Single shared secret | Private key signs, public key verifies |
| Verification | Both sides need secret | Anyone with public key can verify |
| Use case | Single service | Multi-service, public key distribution |

**Use RS256 for production**: private key never leaves the auth service; all other services only need the public key.

---

## 3. OAuth2 / OpenID Connect

```
OAuth2 Authorization Code Flow:
1. User clicks "Login with Google"
2. App redirects to Google /authorize?client_id=...&redirect_uri=...&scope=openid+email
3. User authenticates with Google
4. Google redirects back: /callback?code=AUTH_CODE
5. App backend exchanges code for tokens: POST /token with code + client secret
6. Google returns: access_token, id_token (JWT with user info), refresh_token
7. App creates session / issues own JWT
```

Spring Boot config:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
```

---

## 4. OWASP Top 10 for Backend Engineers

### A03: Injection

```java
// SQL Injection — BAD
String query = "SELECT * FROM users WHERE username = '" + username + "'";
// username = "admin' OR '1'='1" → returns all users

// GOOD — parameterized query (JPA, Spring JDBC)
@Query("SELECT u FROM User u WHERE u.username = :username")
Optional<User> findByUsername(@Param("username") String username);
```

### A01: Broken Access Control

```java
// BAD — IDOR vulnerability
@GetMapping("/orders/{orderId}")
public Order getOrder(@PathVariable UUID orderId) {
    return orderRepo.findById(orderId).orElseThrow(); // anyone can read any order!
}

// GOOD — verify ownership
@GetMapping("/orders/{orderId}")
public Order getOrder(@PathVariable UUID orderId, Authentication auth) {
    Order order = orderRepo.findById(orderId).orElseThrow();
    if (!order.getCustomerId().equals(UUID.fromString(auth.getName()))) {
        throw new AccessDeniedException("Not your order");
    }
    return order;
}
```

### A02: Cryptographic Failures

```java
// BAD
String hash = DigestUtils.md5Hex(password); // MD5 is fast — brute-forceable

// GOOD — BCrypt with work factor
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
String hash = encoder.encode(password);
boolean match = encoder.matches(rawPassword, hash);
```

### A07: Identification & Authentication Failures

```java
// Secure token storage → never return raw password in API response
// Token expiry → short-lived access token (15min-1hr), longer refresh token
// Brute force protection — rate limit auth endpoints
@RateLimiter(name = "auth", fallbackMethod = "authRateLimitFallback")
@PostMapping("/auth/login")
public AuthResponse login(@Valid @RequestBody LoginRequest req) { ... }
```

### A04: Insecure Design — Secret Management

```java
// BAD — hardcoded secret
private static final String JWT_SECRET = "mySecret123";

// BAD — in application.properties committed to git
// jwt.secret=mySecret123

// GOOD — from env var (injected by Kubernetes Secret / AWS Secrets Manager)
@Value("${JWT_SECRET}")
private String jwtSecret;
```

---

## 5. CORS Configuration

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com")); // explicit, not *
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
    config.setExposedHeaders(List.of("X-Request-ID", "X-RateLimit-Remaining"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

Never use `setAllowedOrigins(List.of("*"))` with `setAllowCredentials(true)` — the browser blocks it.

---

## 6. Interview Q&A

**Q: What is the difference between authentication and authorization?**  
Authentication is verifying who you are (login, JWT validation). Authorization is verifying what you are allowed to do (role checks, ownership). A request can be authenticated (valid JWT) but unauthorized (trying to access someone else's resource). Spring Security models this as: `AuthenticationManager` handles authentication, `AccessDecisionManager` handles authorization.

**Q: Why use RS256 over HS256 for JWT in microservices?**  
HS256 uses a shared secret — every service that needs to validate a token must know the secret, and a compromise of any service compromises all tokens. RS256 uses asymmetric keys: the auth service signs with a private key, and all other services verify with the public key. The private key never leaves the auth service. You can expose the public key on a JWKS endpoint so services auto-fetch it.

**Q: What is IDOR and how do you prevent it?**  
Insecure Direct Object Reference — a user can access another user's resource by guessing/changing an ID in the request. Prevention: always check ownership or permission on every request. Don't rely on IDs being unpredictable (even UUIDs don't prevent IDOR, they just make guessing harder). Apply authorization checks at the service/repository layer, not just at the controller entry point.
