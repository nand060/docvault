# Project Overview: DocVault

This document explains the backend, frontend, and Docker setup for the DocVault app.
It is written for someone new to Spring Boot, Java, React, and Docker.

---

## 1. Backend Overview

The backend is a Spring Boot application in `backend/src/main/java/com/yourname/docvault`.
It is structured around HTTP controllers, services, data models, persistence, authentication, and websocket streaming.

### 1.1 `DocvaultApplication.java`
- Entry point for the Spring Boot app.
- `@SpringBootApplication` enables component scanning, auto-configuration, and property support.
- `SpringApplication.run(...)` boots the Spring context and starts the embedded server.
- Importance: Without this file the app will not start.

### 1.2 `auth` package
This handles registration, login, JWT creation, authentication, and request security.

#### `AuthController.java`
- Defines REST endpoints under `/api/auth`.
- `@RestController` exposes methods as JSON HTTP endpoints.
- `@PostMapping("/register")` and `@PostMapping("/login")` define register/login routes.
- Calls `AuthService` to perform real authentication work.
- Importance: It is the public interface for users to get tokens.

#### `AuthRequests.java`
- Contains nested records for incoming request bodies.
- `RegisterRequest` and `LoginRequest` define expected JSON fields.
- `@Valid` on controller methods uses these records to validate input.
- Importance: Defines the shape of login/register payloads and helps validate bad input.

#### `AuthResponse.java`
- A record with `token`, `userId`, `username`, and `aiAccess`.
- Returned after successful login/register.
- Importance: Encapsulates auth response data for the frontend.

#### `AuthService.java`
- Handles business logic for register and login.
- Uses `UserRepository` to access user data.
- `PasswordEncoder` hashes passwords securely with bcrypt.
- `JwtService` creates JWT tokens after successful auth.
- Throws `ApiException` for invalid credentials or duplicates.
- Importance: Central auth logic; separates security details from controllers.

#### `CurrentUser.java`
- Custom annotation for controller method parameters.
- `@AuthenticationPrincipal` injects the authenticated user principal from Spring Security.
- Importance: Lets controllers access the current JWT-authenticated user cleanly.

#### `JwtAuthenticationFilter.java`
- Runs once per request before controllers.
- Extracts `Authorization: Bearer ...` header.
- Parses JWT using `JwtService`.
- Stores authenticated user in `SecurityContextHolder` if valid.
- Importance: Enables stateless authentication using JWT for every API request.

#### `JwtService.java`
- Builds and parses JWT tokens.
- Uses a secret from `application.yml` / environment variable.
- `generateToken(...)` creates a signed token with expiration and custom claims.
- `parseToken(...)` verifies token signature and returns user info.
- Importance: Core of auth security; controls session validity.

#### `SecurityConfig.java`
- Configures Spring Security rules.
- Disables CSRF because this app is API-first.
- Configures CORS using allowed front-end origins.
- Sets session management to stateless (no server session storage).
- Allows public access to `/api/auth/register`, `/api/auth/login`, and websocket endpoints.
- Requires authentication for all other routes.
- Registers `JwtAuthenticationFilter` before username/password filters.
- Provides beans for `PasswordEncoder` and `AuthenticationManager`.
- Importance: Defines who can access what and how tokens are handled.

#### `UserPrincipal.java`
- A simple record representing the authenticated user inside Spring Security.
- Contains `id` and `username`.
- Importance: Used by controllers and websocket auth to identify the current user.

### 1.3 `common` package
This handles consistent error handling.

#### `ApiError.java`
- Simple record with `error` and HTTP `status`.
- Serialized into JSON responses for errors.
- Importance: Standardizes HTTP error payload structure.

#### `ApiException.java`
- Custom runtime exception carrying an HTTP status.
- Thrown from service code to return meaningful errors.
- Importance: Makes it easy to abort service logic with a specific status.

#### `GlobalExceptionHandler.java`
- `@RestControllerAdvice` intercepts exceptions from controllers.
- Converts `ApiException`, validation failures, file upload errors, and other exceptions into JSON `ApiError` responses.
- Returns proper HTTP status codes like `400`, `401`, `403`, `404`, and `500`.
- Importance: Ensures consistent API error behavior and user-friendly messages.

### 1.4 `external` package
This handles external AI APIs: HuggingFace embeddings and Groq streaming.

#### `HuggingFaceClient.java`
- Sends text to HuggingFace inference API for embeddings.
- Uses `WebClient` to make HTTP POST requests.
- Defines `embed(String text)`:
  - Sends `{"inputs": text}` to the HuggingFace model endpoint.
  - Retries on rate limit or service unavailable errors.
  - Parses the returned JSON array into a Java `List<Double>`.
  - Validates that the returned embedding has 384 dimensions.
- Importance: Converts user query and file content into vectors for semantic search.

#### `GroqClient.java`
- Streams AI summary text from the Groq OpenAI-compatible API.
- Builds a streaming completion request with `messages`.
- Uses server-sent events (`text/event-stream`) to receive chunks.
- Parses `data:` lines from the stream and emits `delta.content` tokens.
- If streaming returns no tokens, falls back to a one-shot completion request.
- Importance: Produces the AI summary displayed to the user and enables incremental token streaming.

### 1.5 `file` package
This handles file upload, storage, metadata, and vector creation.

#### `FileController.java`
- REST endpoints under `/api/files`.
- `upload(...)` accepts a multipart text file and forwards it to `FileService`.
- `list(...)` returns the current user's uploaded files.
- `delete(...)` removes a file by ID.
- Importance: Exposes file management APIs to the frontend.

#### `FileService.java`
- Core file upload and deletion logic.
- Validates file type, size, and name.
- Reads file content as UTF-8 text.
- Saves file metadata in the `files` table via `FileRepository`.
- Sends websocket progress updates during upload and embedding.
- Generates and stores embeddings via `HuggingFaceClient` / `FileVectorRepository`.
- Importance: Coordinates persistence, validation, user ownership, and embedding creation.

#### `FileEntity.java`
- JPA entity mapped to the `files` database table.
- Fields:
  - `id` — database primary key.
  - `user` — many-to-one relation to `User`.
  - `name` — sanitized filename.
  - `content` — text content stored in a `TEXT` column.
  - `uploadedAt` — automatic timestamp set before insert.
- Importance: Represents stored files in the database.

#### `FileRepository.java`
- Spring Data JPA repository for `FileEntity`.
- Provides methods to list user files and find a file by its ID and owner.
- Importance: Makes basic file persistence and query easy with Spring Data.

#### `FileResponse.java`
- DTO returned to the frontend after file operations.
- Contains `id`, `name`, `uploadedAt`.
- Importance: Limits the fields sent to the client and decouples API response from entity internals.

#### `FileVectorRepository.java`
- Writes vector embeddings into the `file_vectors` table using plain JDBC.
- `insertVector(...)` stores `file_id` and the vector literal.
- `toVectorLiteral(...)` converts Java `List<Double>` into PostgreSQL vector syntax like `[0.1,0.2,...]`.
- Importance: Saves vector embeddings for semantic search, using SQL because pgvector is not handled by JPA automatically.

### 1.6 `search` package
This handles queries, semantic search, and AI summary orchestration.

#### `SearchController.java`
- `POST /api/search` endpoint.
- Reads the current user via `@CurrentUser`.
- Passes the query text to `SearchService`.
- Importance: Single endpoint for both semantic search and AI summary results.

#### `SearchRequest.java`
- Record representing the body of `/api/search`.
- Contains one field: `query`.
- `@NotBlank` and `@Size(max = 2000)` ensure valid search text.
- Importance: Input validation for search requests.

#### `SearchResponse.java`
- Record returned from search operations.
- Contains `mode`, `results`, and `message`.
- `mode` differentiates `semantic` versus `ai` results.
- Importance: Standardizes the search API response.

#### `SearchDocument.java`
- Record representing a matched file plus optionally returned text.
- Contains `id`, `name`, `content`, and `similarityScore`.
- Includes `toResult()` to convert to a lighter response object.
- Importance: Carries search result metadata and document text for AI summarization.

#### `SearchResult.java`
- Lightweight result DTO with `id`, `name`, and `similarityScore`.
- Returned to the frontend for result display.
- Importance: Avoids sending full file text when only metadata is needed.

#### `VectorSearchRepository.java`
- Performs pgvector similarity search using JDBC.
- Uses SQL with `<=>` operator to compute distance.
- Selects either the file text or an empty string based on `aiMode`.
- Orders by similarity and limits results using `topK` from configuration.
- Importance: Implements the core semantic search logic with Postgres pgvector.

#### `SearchService.java`
- Implements the search workflow.
- Steps:
  1. Validate user with `UserService`.
  2. Create query embedding via `HuggingFaceClient`.
  3. Search the vector store using `VectorSearchRepository`.
  4. If AI mode is off, return semantic results.
  5. If AI mode is on, stream summary tokens via `GroqClient` and websocket.
- Sends websocket events: `status`, `ai-start`, `ai-token`, and `done`.
- Importance: Coordinates query embedding, vector search, and AI summarization.

### 1.7 `user` package
This manages the authenticated user and AI access toggle.

#### `User.java`
- JPA entity mapped to the `users` table.
- Fields:
  - `id`, `username`, `password`, `aiAccess`, `createdAt`.
- `@PrePersist` sets `createdAt` automatically.
- Importance: Stores the user account and AI access preference.

#### `UserRepository.java`
- Spring Data JPA repository for `User`.
- Methods to find by username and check existence.
- Importance: Supports authentication and user lookup.

#### `UserService.java`
- Loads users by ID and updates `aiAccess`.
- `requireUser(...)` throws if the user does not exist.
- Importance: Centralizes user validation and preference updates.

#### `UserController.java`
- `GET /api/users/me` returns current user info.
- `PUT /api/users/ai-access` toggles AI mode.
- Importance: Exposes user profile and AI access controls.

#### `AiAccessRequest.java`
- Record for the request body that toggles AI access.
- Contains one boolean field: `aiAccess`.
- Importance: Validates user preference updates.

#### `UserResponse.java`
- DTO returned to the client for user info.
- Contains `id`, `username`, and `aiAccess`.
- Importance: Decouples internal user entity from what the frontend sees.

### 1.8 `websocket` package
This handles real-time status and token streaming to the frontend.

#### `SocketEvent.java`
- Record with `type` and `payload`.
- Used for all websocket messages sent to the client.
- Importance: Standardizes websocket event shape.

#### `WebSocketConfig.java`
- Configures STOMP over SockJS websocket behavior.
- `configureMessageBroker(...)` enables a simple broker for `/topic`.
- `registerStompEndpoints(...)` exposes `/ws` as the websocket endpoint.
- `configureClientInboundChannel(...)` authenticates websocket CONNECT frames using JWT.
- Verifies users only subscribe to their own topics.
- Importance: Enables secure websocket communication for upload and search events.

---

## 2. Frontend Overview

The frontend is a React app in `frontend/src`. It handles login, file upload, search queries, AI summary mode, and websocket updates.

### 2.1 `main.jsx`
- Root entrypoint for Vite.
- Imports global CSS and the `App` component.
- Wraps the app in `BrowserRouter` for SPA routing.
- `React.StrictMode` enables extra runtime checks during development.
- Importance: Bootstraps the React application into the browser.

### 2.2 `App.jsx`
- Defines client-side routes using React Router.
- Provides `Login`, `Register`, and `Dashboard` pages.
- Uses `ProtectedRoute` to block access to `/dashboard` for unauthenticated users.
- Importance: Controls the app’s page structure and authentication gating.

### 2.3 `api/client.js`
- Axios HTTP client used by all frontend API requests.
- Uses `VITE_API_BASE_URL` or defaults to `http://127.0.0.1:8080`.
- Adds `Authorization` header from local storage on every request.
- Clears auth tokens on 401 unauthorized responses.
- Importance: Centralizes API configuration and auth header injection.

### 2.4 `hooks/useAuth.jsx`
- React context provider for authentication state.
- Stores `token` and `user` data in local storage.
- On mount, validates the token by fetching `/api/users/me`.
- Provides `login`, `register`, `logout`, and `updateUser` helpers.
- `useAuth()` returns auth state to child components.
- Importance: Manages session persistence, login/register flow, and user context.

### 2.5 `pages/Login.jsx`
- Login page UI.
- Collects email and password.
- Calls `login(...)` from `useAuth`.
- Handles submit state, errors, and navigation.
- Importance: Allows users to sign in and get a JWT.

### 2.6 `pages/Register.jsx`
- Registration page UI.
- Collects email and password.
- Calls `register(...)` from `useAuth`.
- Importance: Creates a new account and logs the user in.

### 2.7 `pages/Dashboard.jsx`
- Main authenticated app page.
- Loads file list and websocket subscriptions.
- Subscribes to `/topic/upload-progress/{userId}` and `/topic/search-results/{userId}`.
- Stores file list, upload progress, and search events in React state.
- Refreshes files after upload or delete.
- Updates AI access toggle and user info.
- Importance: Orchestrates the app’s main dashboard and real-time UX.

### 2.8 `websocket/stompClient.js`
- Creates a STOMP websocket client using `@stomp/stompjs` and `sockjs-client`.
- Connects to the backend `/ws` endpoint.
- Sends JWT in the `Authorization` header during websocket CONNECT.
- Uses `reconnectDelay` so the client will retry automatically.
- Importance: Provides the websocket connection used for progress updates and AI token streaming.

### 2.9 `components/UploadPanel.jsx`
- File upload UI and drag/drop zone.
- Validates `.txt` file extension.
- Sends multipart upload to `/api/files/upload`.
- Shows uploading state and errors.
- Displays progress messages from websocket events.
- Importance: Main file ingestion UI; connects file uploads to the backend.

### 2.10 `components/FilesPanel.jsx`
- Displays a list of uploaded files.
- Shows uploaded date and delete button for each file.
- Contains the AI access toggle.
- Importance: Lets users manage files and enable/disable AI summary mode.

### 2.11 `components/SearchPanel.jsx`
- Search box and search output UI.
- Sends `POST /api/search` with the query.
- In AI mode, waits for websocket events instead of plain HTTP response text.
- Displays status updates, results, and streaming summary tokens.
- Shows a friendly message when websocket is not ready.
- Importance: Directly powers both semantic search and AI summary UX.

### 2.12 `styles/app.css`
- Global CSS for layout, form styling, panels, buttons, and responsive behavior.
- Defines color variables, typography, spacing, and hover states.
- Importance: Gives the app its polished UI and consistent styling.

---

## 3. Docker and Database

### 3.1 `docker-compose.yml`
Defines two local services:

- `postgres`:
  - Uses `pgvector/pgvector:pg15` image.
  - Exposes port `15432` on localhost to container port `5432`.
  - Stores data in a named volume `postgres_data`.
  - Runs a healthcheck using `pg_isready`.
- `redis`:
  - Uses `redis:7-alpine` image.
  - Exposes local port `16379` to container port `6379`.

Important notes:
- This app currently does not appear to use Redis, but Redis is available if needed.
- The Postgres container is the only required database for normal operation.
- `docker compose up -d` starts these containers in the background.

### 3.2 What Docker does
- Docker packages applications into containers.
- A container is an isolated environment with its own filesystem, network, and process tree.
- `docker-compose.yml` orchestrates multiple containers together as services.
- In this project, Docker is used to run Postgres and Redis without installing them directly on your machine.
- The backend and frontend run outside Docker in this repository, but they connect to the Dockerized Postgres database.

### 3.3 How to start the database
From the repository root:

```bash
docker compose up -d
```

This command:
- downloads the `pgvector` and `redis` images if necessary,
- starts the Postgres and Redis containers,
- creates the persistent volume `postgres_data`.

To stop the containers:

```bash
docker compose down
```

### 3.4 How to inspect the database tables
Once Postgres is running, use `docker compose exec` to open a Postgres shell:

```bash
docker compose exec postgres psql -U docvault -d docvault
```

Inside `psql`, useful commands:

- List tables:
  ```sql
  \dt
  ```
- Describe a table:
  ```sql
  \d files
  \d users
  \d file_vectors
  ```
- View rows:
  ```sql
  SELECT * FROM users;
  SELECT * FROM files LIMIT 20;
  SELECT * FROM file_vectors LIMIT 20;
  ```
- Exit psql:
  ```sql
  \q
  ```

### 3.5 What the database tables likely contain
Based on the backend code and JPA entities:

- `users`
  - `id`
  - `username`
  - `password` (hashed)
  - `ai_access` (boolean)
  - `created_at`

- `files`
  - `id`
  - `user_id`
  - `name`
  - `content`
  - `uploaded_at`

- `file_vectors`
  - `file_id`
  - `embedding` (pgvector column)

### 3.6 How backend connects to the database
- Connection info is configured by environment variables in `backend/src/main/resources/application.yml`.
- The default settings are:
  - `POSTGRES_URL=jdbc:postgresql://127.0.0.1:15432/docvault`
  - `POSTGRES_USER=docvault`
  - `POSTGRES_PASSWORD=docvault`
- If the backend is started from a shell, those variables must be exported before running `mvn spring-boot:run`.

---

## 4. How the pieces fit together

### User flow
1. User registers or logs in via the React frontend.
2. The frontend stores a JWT token and user information in local storage.
3. The dashboard opens and a websocket connection is established using that JWT.
4. The user uploads a `.txt` file.
5. Backend validates the file, saves it to Postgres, and generates a vector embedding with the HuggingFace API.
6. The user searches for text.
7. The backend computes an embedding for the search query using HuggingFace.
8. The backend performs vector similarity search in Postgres.
9. If AI mode is enabled, the backend streams summary tokens from Groq over websocket.
10. The frontend renders tokens in real time and shows the final result.

### Why the backend is split this way
- Controllers handle HTTP routing and request binding.
- Services hold business logic and coordinate components.
- Repositories handle persistence and database access.
- External clients isolate third-party API calls.
- Common classes standardize error handling.
- Websocket setup separates real-time event delivery from normal REST traffic.

### Why the frontend is split this way
- `App.jsx` and `main.jsx` bootstrap the React app and routing.
- `useAuth.jsx` manages authentication and session state across the app.
- `api/client.js` centralizes API calls and auth headers.
- `pages/*` define screens and user flow.
- `components/*` isolate reusable UI pieces like uploading, file lists, and search.
- `stompClient.js` isolates websocket connection details.
- `styles/app.css` holds layout and styling.

---

## 5. Practical tips for a beginner

- In Spring Boot, `@RestController` + HTTP annotations create endpoints.
- `@Service` marks business logic classes.
- `@Repository` or `JpaRepository` handles database access.
- `@Transactional` controls database transaction boundaries.
- In React, hooks like `useState` and `useEffect` manage UI state and side effects.
- `localStorage` keeps auth tokens across refreshes.
- Docker containers are isolated apps; they are not the same as files on your machine.
- `docker compose exec` lets you run commands inside a container.

If you want, I can also produce a second markdown file that maps every backend file to the exact HTTP route(s) it serves and every frontend component to the UI section it renders.