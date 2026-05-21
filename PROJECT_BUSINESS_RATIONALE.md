# Project Business Rationale: DocVault

This file contains per-file business-focused rationales for the repository. It complements `PROJECT_OVERVIEW.md` by focusing on product, operational, and go-to-market reasons each file exists.

## Backend files

- `DocvaultApplication.java` — Boots the runnable product; needed so customers or internal testers can start the service locally or in CI.

### Auth
- `AuthController.java` — Public auth endpoints used for onboarding and login; vital for user acquisition and initial conversion.
- `AuthRequests.java` — Explicit request shapes reduce onboarding friction and errors from malformed requests.
- `AuthResponse.java` — Provides the client with the minimal fields to proceed (token, id, ai flag) enabling personalization.
- `AuthService.java` — Implements authentication business rules: prevents duplicate accounts and enforces password policies, reducing fraud and support overhead.
- `CurrentUser.java` — Improves developer productivity when adding new user-facing features, reducing time-to-market.
- `JwtAuthenticationFilter.java` — Enables stateless JWT sessions so the product scales to many users without session storage costs.
- `JwtService.java` — Centralizes token behavior for security/compliance; critical for audits and incident response.
- `SecurityConfig.java` — Gatekeeper rules for which APIs are public; required to protect paid features and internal endpoints.
- `UserPrincipal.java` — Canonical identity type used across services for consistent authorization decisions.

### Common
- `ApiError.java` — Predictable error payloads make customer support and client UX easier to implement and track.
- `ApiException.java` — Allows service-layer code to signal business-level errors that should be surfaced to customers.
- `GlobalExceptionHandler.java` — Converts technical problems into actionable messages, reducing support load.

### External
- `HuggingFaceClient.java` — Embeddings are core to product differentiation; embedding quality directly impacts search relevance and perceived value.
- `GroqClient.java` — AI summaries are a premium feature; streaming reduces latency and improves engagement.

### File
- `FileController.java` — Public API for uploading and managing documents; necessary for the core product flow (ingest → search).
- `FileService.java` — Ensures only supported content is stored and triggers embedding generation; prevents bad content from inflating API costs.
- `FileEntity.java` — Stores persisted file metadata for audit and retention policies.
- `FileRepository.java` — Data access for file features (list, delete) supporting product operations and cleanup.
- `FileResponse.java` — Limits sensitive fields returned to frontends and serves marketing-friendly metadata.
- `FileVectorRepository.java` — Persists embeddings; crucial for delivering fast, scalable semantic search.

### Search
- `SearchController.java` — Offers a single gateway for search and AI summaries so usage can be tracked and monetized.
- `SearchRequest.java` — Guards against excessively large queries that could generate outsized costs.
- `SearchResponse.java` — Signals search mode (semantic vs ai) so frontend can present upsell or usage analytics.
- `SearchDocument.java` — Carries context to AI summarization; richer context leads to higher-value summaries.
- `SearchResult.java` — Minimal result payload reduces bandwidth and speeds list rendering.
- `SearchService.java` — Orchestrates expensive operations and emits websocket events for UX; its structure enables feature gating and throttling.
- `VectorSearchRepository.java` — Implements pgvector search, enabling low-latency semantic queries—a key product differentiator.

### User
- `User.java` — Stores account and feature flags (like `aiAccess`) enabling feature gating and possible tiered billing.
- `UserRepository.java` — Fast lookups supporting authentication, analytics, and admin tooling.
- `UserService.java` — Central place for policy changes (e.g., enabling AI for a cohort) for marketing experiments.
- `UserController.java` — Exposes profile and AI access toggles to the frontend for self-serve upgrades and settings.
- `AiAccessRequest.java` — A simple contract making it easy to track AI feature toggles in analytics.
- `UserResponse.java` — A small shape for the frontend to react to user features and promote upsells.

### Websocket
- `SocketEvent.java` — Standard event shape simplifies analytics (event types can be counted in logs) and reduces frontend complexity.
- `WebSocketConfig.java` — Enforces real-time security; real-time UX reduces perceived latency and improves engagement metrics.

## Frontend files

- `main.jsx` — Initializes the customer-facing app; it is the starting point of the customer experience.
- `App.jsx` — Route structure defines the core flows (auth and dashboard) used by marketing funnels.
- `api/client.js` — Central place to enforce authentication and handle 401 flows—important for security and user experience continuity.
- `hooks/useAuth.jsx` — Manages session persistence, which reduces churn by avoiding repeated logins.
- `pages/Login.jsx` / `pages/Register.jsx` — Core acquisition flows; small UX improvements here can materially improve conversion.
- `pages/Dashboard.jsx` — The main product surface where users extract value; key to measuring retention.
- `components/UploadPanel.jsx` — Ingestion UX; fewer errors here means more retained documents and happier users.
- `components/FilesPanel.jsx` — Management UX important for privacy and trust (users can delete content).
- `components/SearchPanel.jsx` — Primary interaction for product value; optimization here drives engagement and perceived intelligence.
- `websocket/stompClient.js` — Handles reliable real-time behavior that improves perceived responsiveness of AI features.
- `styles/app.css` — Branding and consistent style directly influence user trust and perceived polish.

## Docker and infra

- `docker-compose.yml` — Provides a reproducible local dev environment for the database, lowering onboarding friction for internal teams and speeding developer productivity.
- Postgres with `pgvector` is necessary to deliver the semantic search functionality that differentiates the product.
- Redis is included for future needs (caching, rate-limiting) and makes scaling experiments easier without changing infrastructure code.

---

If you'd like, I can now:
- Insert these business rationale entries back into `PROJECT_OVERVIEW.md` inline (I attempted to patch but encountered an error), or
- Produce a separate `FILE_ROUTE_MAP.md` that ties endpoints, methods, and frontend components to the exact files that implement them, or
- Expand any specific file into a line-by-line technical commentary.

Which next step do you want me to take?
