# Project Overview: DocVault — Technical Deep Dive

This is an expanded, detailed companion to `PROJECT_OVERVIEW.md`. It explains **how things actually work**, with code examples and mechanics.

---

## Table of Contents

1. [Spring Boot Annotations & Spring Security](#spring-boot-annotations--spring-security)
2. [JWT Authentication Flow](#jwt-authentication-flow)
3. [Database & JPA Entities](#database--jpa-entities)
4. [Vector Search & pgvector](#vector-search--pgvector)
5. [Websocket/STOMP Real-Time Communication](#websocketstomp-real-time-communication)
6. [React Hooks & State Management](#react-hooks--state-management)
7. [External API Integration](#external-api-integration)
8. [Request/Response Lifecycle](#requestresponse-lifecycle)

---

## Spring Boot Annotations & Spring Security

### What Spring Boot Annotations Actually Do

When you see `@SpringBootApplication` in `DocvaultApplication.java`:

```java
@SpringBootApplication
public class DocvaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocvaultApplication.class, args);
    }
}
```

**What's happening:**

- `@SpringBootApplication` is a **meta-annotation** — it combines three annotations:
  - `@Configuration`: Tells Spring this class defines beans (component definitions).
  - `@EnableAutoConfiguration`: Tells Spring to auto-configure the classpath (e.g., if Spring Data JPA is on the classpath, auto-configure a `DataSource`, `EntityManagerFactory`, etc.).
  - `@ComponentScan`: Tells Spring to scan packages for `@Component`, `@Service`, `@Repository`, `@RestController` and register them as beans.

- `SpringApplication.run(...)` **initializes the Spring context**, meaning:
  1. Scans the classpath for annotated classes.
  2. Creates bean instances in a dependency container.
  3. Wires dependencies together (`@Autowired` or constructor injection).
  4. Starts an embedded Tomcat server on port `8080` (default).

### `@RestController` — HTTP Endpoints

In `AuthController.java`:

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequests.RegisterRequest request) {
        return authService.register(request.username(), request.password());
    }
}
```

**What's happening:**

- `@RestController` = `@Controller` + `@ResponseBody`. It tells Spring:
  - This class handles HTTP requests.
  - Every method **returns JSON** (not a template or view).
  - Spring automatically serializes the returned object to JSON using Jackson.

- `@RequestMapping("/api/auth")` — the **base path** for all endpoints in this controller. Any method here will be routed to `/api/auth/{subpath}`.

- `@PostMapping("/register")` — handles **HTTP POST** to `/api/auth/register`.

- `@Valid` — triggers **validation** on the request body using constraints from the request class:
  ```java
  public record RegisterRequest(
      @Email @NotBlank String username,
      @Size(min = 8, max = 100) String password
  ) {}
  ```
  If validation fails, Spring returns **400 Bad Request** automatically.

- `@RequestBody` — tells Spring to **deserialize the HTTP body JSON** into a Java object. Example:
  ```json
  {
    "username": "user@example.com",
    "password": "password123"
  }
  ```
  becomes `RegisterRequest(username="user@example.com", password="password123")`.

### `@Service` — Business Logic Layer

In `AuthService.java`:

```java
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    
    public AuthService(UserRepository userRepository, ...) {
        this.userRepository = userRepository;
        ...
    }
}
```

**What's happening:**

- `@Service` marks this class as a **Spring bean** containing business logic.
- Spring **automatically instantiates** this class and **injects dependencies**.
- **Constructor injection** (used here) is preferred over `@Autowired` because it makes dependencies explicit and immutable.

### `@Repository` — Data Access Layer

In `UserRepository.java`:

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

**What's happening:**

- `JpaRepository<User, Long>` is an interface that Spring **generates at runtime**.
- Spring Data JPA **reads method names** and generates SQL queries:
  - `findByUsername(String username)` → `SELECT * FROM users WHERE username = ?`
  - `existsByUsername(String username)` → `SELECT 1 FROM users WHERE username = ? LIMIT 1`
- This is called the **Query by Method Name** pattern.

### `@Transactional` — Database Transactions

In `AuthService.java`:

```java
@Transactional
public AuthResponse register(String username, String password) {
    User user = new User();
    user.setUsername(username);
    user.setPassword(passwordEncoder.encode(password));
    User saved = userRepository.save(user);
    // If an exception occurs after this point, the transaction rolls back
    return responseFor(saved);
}
```

**What's happening:**

- `@Transactional` wraps the method in a **database transaction**.
- A transaction is a **sequence of operations that either all succeed or all fail** (ACID semantics).
- Example without `@Transactional`:
  1. Insert user into `users` table. ✓
  2. Crash. ✗
  3. Database now has a broken user record.
  
- Example with `@Transactional`:
  1. Begin transaction.
  2. Insert user. ✓
  3. Crash. ✗
  4. **Automatic rollback**: user record is **never written**.

### `@Entity` & JPA Annotations

In `User.java`:

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
```

**What's happening:**

- `@Entity` tells Spring Data JPA this class maps to a database table.
- `@Table(name = "users")` specifies the **exact table name** (otherwise it would be derived from the class name).
- `@Id` marks `id` as the **primary key**.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` tells JPA:
  - Use the database's **auto-increment** feature.
  - Example PostgreSQL DDL:
    ```sql
    CREATE TABLE users (
        id BIGSERIAL PRIMARY KEY,
        username VARCHAR NOT NULL UNIQUE,
        ...
    );
    ```
  - When you `save()` a user without an `id`, the database generates one.

- `@Column(nullable = false, unique = true)` translates to SQL:
  ```sql
  username VARCHAR NOT NULL UNIQUE
  ```

- `@PrePersist` is a **lifecycle callback** that runs **before the entity is inserted into the database**.
  ```java
  void onCreate() {
      this.createdAt = Instant.now();
  }
  ```
  So every time a `User` is created, the current time is automatically set.

### Spring Security Configuration

In `SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                .permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }
}
```

**What's happening:**

- `@EnableWebSecurity` activates Spring Security filters for **every HTTP request**.

- `.csrf(AbstractHttpConfigurer::disable)` — disables CSRF protection because this is a **stateless API**. (CSRF is mainly for session-based web apps.)

- `SessionCreationPolicy.STATELESS` — tells Spring **never create server-side sessions**. Every request must be authenticated independently (via JWT).

- `.permitAll()` on `/api/auth/register` and `/api/auth/login` — these routes **require no authentication**.

- `.anyRequest().authenticated()` — **every other request** must have a valid JWT.

- `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` — registers a custom filter that **runs before the default auth filter**. This filter extracts the JWT from the `Authorization` header and validates it.

---

## JWT Authentication Flow

### What is JWT?

A **JWT (JSON Web Token)** is a **stateless credential**. Instead of the server storing session data (like a session ID), the server encodes user info **into the token itself**.

A JWT has three parts separated by dots:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwidXNlcklkIjoxfQ.sXDT8G5ZV6zI9pYvD3JJ2pKzXyZ0W5vK8rL2mN3oQ9s
```

1. **Header** (base64url encoded JSON):
   ```json
   {"alg":"HS256"}
   ```

2. **Payload** (base64url encoded JSON):
   ```json
   {"sub":"user@example.com","userId":1,"exp":1716220800}
   ```

3. **Signature** (HMAC using the header + payload + secret):
   ```
   HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
   ```

### JWT Generation

In `JwtService.java`:

```java
public String generateToken(Long userId, String username) {
    Instant now = Instant.now();
    return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("username", username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))  // e.g., 7 days from now
            .signWith(key)  // Sign with the secret key
            .compact();     // Serialize to string
}
```

**What's happening:**

1. Create a builder.
2. Set the **subject** (the username — this is a standard JWT field).
3. Add **custom claims** (userId, username) — these are user-defined fields.
4. Set **expiration** — the token will be invalid after this time. Example: 7 days.
5. **Sign with a secret key** — the signature proves the token wasn't forged.
6. **Compact** — serialize to the `header.payload.signature` string format.

**Example output:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwidXNlcklkIjoxLCJ1c2VybmFtZSI6InVzZXJAZXhhbXBsZS5jb20iLCJleHAiOjE3MjAyMDAwMDB9.xZ7vK9wQ0mN3lP5rS8tU1vW2yX9zA0bC1dE2fG3hI4j
```

### JWT Validation

In `JwtService.java`:

```java
public UserPrincipal parseToken(String token) {
    Claims claims = Jwts.parser()
            .verifyWith(key)        // Verify signature using the secret key
            .build()
            .parseSignedClaims(token)  // Parse and validate
            .getPayload();

    Long userId = claims.get("userId", Long.class);
    String username = claims.get("username", String.class);
    return new UserPrincipal(userId, username);
}
```

**What's happening:**

1. **Verify the signature** — use the secret key to recompute the signature and compare with the token's signature.
   - If someone forged or modified the token, the signature won't match.
   - If the signature is invalid, an exception is thrown.

2. **Parse the payload** — extract the claims (userId, username, expiration).

3. **Check expiration** — if the token has expired, an exception is thrown.

4. **Return a `UserPrincipal`** — a Java object representing the authenticated user.

### End-to-End JWT Flow

**Request:**
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password123"
}
```

**Backend:**
1. Controller receives the request.
2. `AuthService.login()` is called.
3. Look up user by username in the database.
4. Compare the hashed password using `BCrypt`:
   ```java
   if (!passwordEncoder.matches(password, user.getPassword())) {
       throw new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED);
   }
   ```
5. Generate a JWT:
   ```java
   String token = jwtService.generateToken(user.getId(), user.getUsername());
   ```
6. Return:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiJ9...",
     "userId": 1,
     "username": "user@example.com",
     "aiAccess": false
   }
   ```

**Subsequent Requests:**

Client sends:
```
GET /api/files
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Backend:
1. `JwtAuthenticationFilter` extracts the token from the `Authorization` header.
2. Calls `JwtService.parseToken(token)`.
3. If valid, creates a `UserPrincipal` and stores it in the security context.
4. Controller receives the request with authenticated user info via `@CurrentUser`.

---

## Database & JPA Entities

### Entity Relationships

**Users table:**
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR NOT NULL UNIQUE,
    password VARCHAR NOT NULL,
    ai_access BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

**Files table:**
```sql
CREATE TABLE files (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR NOT NULL,
    content TEXT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**File-to-User relationship in Java:**

```java
@Entity
@Table(name = "files")
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String name;
    private String content;
}
```

**What's happening:**

- `@ManyToOne` — many files belong to one user.
- `@JoinColumn(name = "user_id")` — the foreign key column is `user_id` in the `files` table.
- `fetch = FetchType.LAZY` — **don't load the user when loading a file**. Only fetch the `user` if you explicitly access `file.getUser()`.
  - Without `LAZY`, every file fetch would also load the user from the database (performance hit).
  - With `LAZY`, the user is only fetched if needed.

### JPA Queries

When you call:
```java
List<FileEntity> files = fileRepository.findAllByUserIdOrderByUploadedAtDesc(userId);
```

Spring Data JPA **reads the method name** and generates:
```sql
SELECT * FROM files WHERE user_id = ? ORDER BY uploaded_at DESC
```

Method name breakdown:
- `findAll` → SELECT *
- `ByUserId` → WHERE user_id = ?
- `OrderByUploadedAtDesc` → ORDER BY uploaded_at DESC

---

## Vector Search & pgvector

### What is a Vector Embedding?

A **vector embedding** is a list of numbers representing the **semantic meaning** of text.

Example:
- Text: `"The quick brown fox jumps over the lazy dog"`
- Embedding (384-dimensional): `[0.123, -0.456, 0.789, ..., 0.234]`

**Key property:** Similar texts have similar embeddings.

Example:
- Embedding for `"fast brown fox jumps over a lazy dog"`: `[0.125, -0.458, 0.791, ..., 0.236]` (very close to above)
- Embedding for `"the sky is blue"`: `[-0.987, 0.654, -0.321, ..., -0.654]` (very different)

### HuggingFace Embeddings

In `HuggingFaceClient.java`:

```java
public List<Double> embed(String text) {
    List<String> prompts = List.of(text);
    var request = Map.of("inputs", prompts);

    var response = webClient.post()
            .uri(huggingFaceEndpoint)
            .header("Authorization", "Bearer " + huggingFaceToken)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .retryWhen(...)  // Retry on rate limit
            .block();

    // Response: [[0.123, -0.456, ...]]
    JsonNode node = response.get(0);  // First embedding (we sent 1 prompt)
    List<Double> embedding = new ArrayList<>();
    for (JsonNode value : node) {
        embedding.add(value.asDouble());
    }
    return embedding;
}
```

**What's happening:**

1. Send text to HuggingFace API.
2. Receive a list of numbers (384 dimensions).
3. Convert to Java `List<Double>`.

### pgvector Storage

In `FileVectorRepository.java`:

```java
public void insertVector(Long fileId, List<Double> embedding) {
    jdbcTemplate.update(
            "INSERT INTO file_vectors (file_id, embedding) VALUES (?, CAST(? AS vector))",
            fileId,
            toVectorLiteral(embedding)
    );
}

private static String toVectorLiteral(List<Double> embedding) {
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < embedding.size(); i++) {
        if (i > 0) builder.append(',');
        builder.append(embedding.get(i));
    }
    return builder.append(']').toString();
}
```

**What's happening:**

1. Convert the Java list to PostgreSQL vector syntax: `[0.123,-0.456,...]`
2. Insert into the database:
   ```sql
   INSERT INTO file_vectors (file_id, embedding) VALUES (1, '[0.123,-0.456,...]'::vector)
   ```

**Database schema:**
```sql
CREATE TABLE file_vectors (
    file_id BIGINT PRIMARY KEY,
    embedding VECTOR(384),
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);
```

### Vector Similarity Search

In `VectorSearchRepository.java`:

```java
public List<SearchDocument> searchSimilar(List<Double> queryEmbedding, Long userId, int topK, boolean includeContent) {
    String query = """
        SELECT f.id, f.name, 
               CASE WHEN ? THEN f.content ELSE '' END as content,
               1 - (fv.embedding <=> ?) as similarity_score
        FROM files f
        JOIN file_vectors fv ON f.id = fv.file_id
        WHERE f.user_id = ?
        ORDER BY similarity_score DESC
        LIMIT ?
    """;

    return jdbcTemplate.query(query, (rs, rowNum) -> 
        new SearchDocument(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("content"),
            rs.getDouble("similarity_score")
        ),
        includeContent,
        toVectorLiteral(queryEmbedding),
        userId,
        topK
    );
}
```

**What's happening:**

1. **Query embedding** — the embedding of the search query.
2. **`<=>` operator** — pgvector's **distance operator**. It computes the **cosine distance** between two vectors (0 to 2, where 0 = identical).
3. **`1 - distance`** — convert distance to similarity (1 = identical, 0 = opposite).
4. **ORDER BY similarity DESC** — return the most similar files first.
5. **LIMIT topK** — return only the top K results (e.g., top 5).

**Example:**

Query: `"machine learning"` → Embedding: `[0.5, 0.3, ...]`

Files:
- File 1: `"neural networks"` → Embedding: `[0.501, 0.298, ...]` → Similarity: 0.98 ✓
- File 2: `"cooking tips"` → Embedding: `[-0.8, 0.6, ...]` → Similarity: 0.12 ✗

Results ordered: File 1 (0.98), File 2 (0.12)

---

## Websocket/STOMP Real-Time Communication

### Why Websockets?

**HTTP is request-response:**
```
Client: "Give me search results"
Server: "Here are results"
(Connection closes)
```

**Websocket is bidirectional:**
```
Client: (opens connection) "I'm listening"
Server: "Here's status"
Server: "Here's token 1"
Server: "Here's token 2"
Server: "Done"
```

### STOMP Protocol

STOMP (Simple Text Oriented Messaging Protocol) is a **protocol on top of Websocket** that provides:
- Message framing
- Subscriptions
- Acknowledgments

### Connection Flow

**Frontend (`stompClient.js`):**

```javascript
const client = new Client({
    webSocketFactory: () => new SockJS(`http://localhost:8080/ws`),
    connectHeaders: {
        Authorization: `Bearer ${token}`
    },
    reconnectDelay: 3000,
    onConnect: () => {
        // Subscribe to AI token stream
        client.subscribe('/topic/search-results/' + userId, (message) => {
            const event = JSON.parse(message.body);
            console.log('Token received:', event);
        });
    }
});

client.activate();  // Start the connection
```

**What's happening:**

1. Create a **SockJS connection** to `/ws` (Websocket with fallback to long-polling).
2. Send the JWT in **connect headers** (not a standard HTTP header — custom STOMP header).
3. `onConnect` callback is called once connected.
4. **Subscribe** to `/topic/search-results/{userId}` — tells the server "send me messages to this topic".

**Backend (`WebSocketConfig.java`):**

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins(allowedOrigins.split(","))
            .withSockJS();
}

@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = firstNativeHeader(accessor, "Authorization");
                UserPrincipal principal = jwtService.parseToken(authHeader.substring(7));
                accessor.setUser(new SocketPrincipal(principal.id(), principal.username()));
            }
            
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                Principal principal = accessor.getUser();
                String destination = accessor.getDestination();
                // Prevent user from subscribing to another user's topic
                if (!ownsTopic(principal.getName(), destination)) {
                    throw new AccessDeniedException("Cannot subscribe to another user's topic");
                }
            }
            return message;
        }
    });
}
```

**What's happening:**

1. **CONNECT frame** — client connects. Extract JWT from headers and authenticate.
2. **SUBSCRIBE frame** — client subscribes to a topic. Verify the user owns the topic (can't spy on other users).

### Sending Tokens

**Backend (`SearchService.java`):**

```java
@Transactional
public void search(Long userId, String query) {
    // ... search logic ...
    
    messagingTemplate.convertAndSend(
        "/topic/search-results/" + userId,
        SocketEvent.of("ai-start", "Starting AI summary")
    );
    
    // Stream tokens
    groqClient.streamSummary(documents, (token) -> {
        messagingTemplate.convertAndSend(
            "/topic/search-results/" + userId,
            SocketEvent.of("ai-token", token)
        );
    });
    
    messagingTemplate.convertAndSend(
        "/topic/search-results/" + userId,
        SocketEvent.of("done", "Search complete")
    );
}
```

**What's happening:**

1. Send an `ai-start` event to all subscribers of `/topic/search-results/{userId}`.
2. For each token received from Groq, send an `ai-token` event.
3. Send a `done` event when finished.

**Frontend receives:**

```javascript
client.subscribe('/topic/search-results/' + userId, (message) => {
    const event = JSON.parse(message.body);
    
    if (event.type === 'ai-token') {
        setTokens(prev => [...prev, event.payload]);  // Add token to UI
    }
    if (event.type === 'done') {
        setLoading(false);
    }
});
```

---

## React Hooks & State Management

### `useState` — Managing Component State

In `SearchPanel.jsx`:

```jsx
const [query, setQuery] = useState('');
const [results, setResults] = useState([]);
const [tokens, setTokens] = useState([]);
const [loading, setLoading] = useState(false);
```

**What's happening:**

- `useState` is a React hook that creates a **state variable** and a **function to update it**.
- `query` — current input value.
- `setQuery` — function to update `query` and **re-render the component**.
- The **initial value** (empty string, empty array, etc.) is used when the component mounts.

When you call `setQuery('hello')`:
1. React updates the internal state.
2. **Re-renders** the component with the new value.
3. The UI updates automatically.

### `useEffect` — Side Effects

In `Dashboard.jsx`:

```jsx
useEffect(() => {
    // This runs once when the component mounts
    fetchFiles();
    connectWebsocket();
    
    // Cleanup function (runs when component unmounts)
    return () => {
        websocket.disconnect();
    };
}, []);  // Empty dependency array = run only on mount
```

**What's happening:**

- `useEffect` runs **after the component renders**.
- The **dependency array** `[]` means "run this once on mount".
- The **cleanup function** runs when the component unmounts (cleanup resources).

Example with dependencies:

```jsx
useEffect(() => {
    console.log('User ID changed:', userId);
}, [userId]);  // Run when userId changes
```

This runs:
1. On mount.
2. Every time `userId` changes.

### Custom Hook: `useAuth`

In `hooks/useAuth.jsx`:

```jsx
export function useAuth() {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [user, setUser] = useState(null);
    
    useEffect(() => {
        // On mount, check if token exists and is valid
        const token = localStorage.getItem('token');
        if (token) {
            api.get('/api/users/me')
                .then(res => setUser(res.data))
                .catch(() => logout());
        }
    }, []);
    
    const login = async (username, password) => {
        const res = await api.post('/api/auth/login', { username, password });
        localStorage.setItem('token', res.data.token);
        setUser(res.data);
        setIsAuthenticated(true);
    };
    
    const logout = () => {
        localStorage.removeItem('token');
        setUser(null);
        setIsAuthenticated(false);
    };
    
    return { isAuthenticated, user, login, logout };
}
```

**What's happening:**

1. Store auth state (token, user).
2. On mount, verify the token is still valid by calling `/api/users/me`.
3. Provide `login()` function that:
   - Calls the backend.
   - Stores the token in `localStorage` (persistent across page refreshes).
   - Updates local state.
4. Provide `logout()` function that clears everything.

### Context API — Sharing State

In `hooks/useAuth.jsx`:

```jsx
const AuthContext = createContext();

export function AuthProvider({ children }) {
    const [state, setState] = useState({ /* ... */ });
    
    return (
        <AuthContext.Provider value={state}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    return useContext(AuthContext);
}
```

**What's happening:**

- `createContext` creates a **global state store**.
- `AuthProvider` wraps the app and provides auth state to **all descendant components**.
- Any component can call `useAuth()` to access the state **without prop drilling**.

Example:
```jsx
// Dashboard.jsx
const { user, isAuthenticated } = useAuth();

// SearchPanel.jsx (deeply nested)
const { user } = useAuth();  // No need to pass props from Dashboard
```

---

## External API Integration

### HuggingFace Embeddings

The app calls HuggingFace to convert text to embeddings:

```
Text: "machine learning"
       ↓ (HTTP POST to HuggingFace)
Embedding: [0.5, 0.3, -0.2, ..., 0.1]
```

**Retry logic** (in case HuggingFace is rate-limited):

```java
.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
    .filter(ex -> ex instanceof WebClientRequestException)
    .doBeforeRetry(signal -> 
        log.warn("HuggingFace request failed, retrying: {}", signal.failure().getMessage())
    )
)
```

This means:
- Retry up to 3 times.
- Wait 1 second, then 2 seconds, then 4 seconds (exponential backoff).
- Only retry on specific exceptions.

### Groq Streaming

The app calls Groq to stream AI summaries as **Server-Sent Events (SSE)**:

```java
public void streamSummary(List<SearchDocument> documents, Consumer<String> onToken) {
    var request = buildRequest(documents);
    
    webClient.post()
            .uri(groqEndpoint)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class)  // Read the response stream as multiple strings
            .doOnNext(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    Map<String, Object> event = objectMapper.readValue(data, Map.class);
                    
                    // Navigate nested object: event["choices"][0]["delta"]["content"]
                    List<Map> choices = (List<Map>) event.get("choices");
                    Map delta = (Map) choices.get(0).get("delta");
                    String content = (String) delta.get("content");
                    
                    if (content != null) {
                        onToken.accept(content);  // Pass token to consumer
                    }
                }
            })
            .subscribe();
}
```

**What's happening:**

1. **POST to Groq** with streaming enabled.
2. **SSE format** — response is a stream of lines like:
   ```
   data: {"choices": [{"delta": {"content": "The"}}]}
   data: {"choices": [{"delta": {"content": " quick"}}]}
   data: {"choices": [{"delta": {"content": " brown"}}]}
   ```
3. **Parse each line** — extract the `content` field.
4. **Call `onToken()`** for each token.

---

## Request/Response Lifecycle

### File Upload Lifecycle

1. **Frontend (`UploadPanel.jsx`):**
   ```jsx
   const onFileChange = (file) => {
       const formData = new FormData();
       formData.append('file', file);
       
       api.post('/api/files/upload', formData)
           .then(res => console.log('Upload complete:', res.data))
           .catch(err => console.error('Upload failed:', err));
   };
   ```

2. **HTTP Request:**
   ```
   POST /api/files/upload
   Content-Type: multipart/form-data
   Authorization: Bearer eyJ...
   
   [binary file data]
   ```

3. **Backend (`FileController.java`):**
   ```java
   @PostMapping("/upload")
   public FileResponse upload(@CurrentUser UserPrincipal principal, 
                              @RequestParam("file") MultipartFile file) {
       return fileService.upload(principal.id(), file);
   }
   ```
   - `@CurrentUser` extracts the authenticated user from the JWT.
   - `@RequestParam("file")` extracts the file from the multipart data.

4. **Backend (`FileService.java`):**
   ```java
   @Transactional
   public FileResponse upload(Long userId, MultipartFile multipartFile) {
       // Validate
       validateUpload(multipartFile);  // Check extension, size
       
       // Save to DB
       FileEntity file = new FileEntity();
       file.setUser(userService.requireUser(userId));
       file.setName(safeName(multipartFile.getOriginalFilename()));
       file.setContent(readContent(multipartFile));
       FileEntity saved = fileRepository.saveAndFlush(file);  // INSERT INTO files
       
       // Generate embedding
       sendProgress(userId, "embedding", "Generating embedding");
       List<Double> embedding = huggingFaceClient.embed(file.getContent());
       fileVectorRepository.insertVector(saved.getId(), embedding);  // INSERT INTO file_vectors
       
       // Send completion event
       sendProgress(userId, "done", "Done");
       
       return FileResponse.from(saved);  // Convert to DTO
   }
   ```

5. **WebSocket Progress:**
   - During upload, messages are sent to `/topic/upload-progress/{userId}`:
     ```json
     {"type": "uploading", "payload": "Uploading file"}
     {"type": "embedding", "payload": "Generating embedding"}
     {"type": "done", "payload": "Done"}
     ```

6. **Frontend receives:**
   ```jsx
   client.subscribe(`/topic/upload-progress/${userId}`, (message) => {
       const event = JSON.parse(message.body);
       setUploadProgress(event);
   });
   ```

7. **HTTP Response:**
   ```json
   {
     "id": 1,
     "name": "document.txt",
     "uploadedAt": "2024-05-19T10:00:00Z"
   }
   ```

### Search + AI Summary Lifecycle

1. **Frontend sends search:**
   ```jsx
   api.post('/api/search', { query: 'machine learning' })
       .then(response => {
           if (aiMode) {
               // Don't use the response; wait for websocket tokens
           } else {
               setResults(response.data.results);
           }
       });
   ```

2. **Backend:**
   - Generates query embedding via HuggingFace.
   - Searches `file_vectors` table using pgvector `<=>`.
   - If AI mode:
     - Starts streaming tokens from Groq.
     - Sends tokens via websocket.

3. **Websocket messages:**
   ```json
   {"type": "status", "payload": "Searching..."}
   {"type": "ai-start", "payload": "Starting AI summary"}
   {"type": "ai-token", "payload": "The"}
   {"type": "ai-token", "payload": " quick"}
   {"type": "ai-token", "payload": " brown"}
   {"type": "done", "payload": "Complete"}
   ```

4. **Frontend accumulates tokens:**
   ```jsx
   const [tokens, setTokens] = useState([]);
   
   client.subscribe(`/topic/search-results/${userId}`, (message) => {
       const event = JSON.parse(message.body);
       if (event.type === 'ai-token') {
           setTokens(prev => [...prev, event.payload]);
       }
   });
   
   // Render tokens joined into a string:
   const summary = tokens.join('');
   ```

---

This covers the core mechanics! Let me know if you'd like me to expand any specific area further.
