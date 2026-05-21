# Engineering Decisions

## Redis

The project includes a Redis container in `docker-compose.yml` to preserve the requested infrastructure shape, but the application does not use Redis in code.

The user explicitly chose to skip Redis. That means:

- JWTs are stateless and expire after 24 hours.
- There is no token blacklist or refresh token flow.
- Embeddings are requested directly from HuggingFace without Redis caching.
- Uploads and searches run synchronously because files are limited to 1MB and each file has one embedding.

If Redis is enabled later, the best first use would be an embedding cache keyed by a SHA-256 hash of input text. That would reduce free-tier API calls without changing product behavior.

## Vector Storage

Hibernate entities model users and files. Vector writes and KNN search use `JdbcTemplate` with explicit `CAST(? AS vector)` bindings because pgvector’s column type is not a native JPA scalar. This keeps the critical query readable and ensures ownership filtering is in SQL.

## AI Privacy Boundary

File content is sent to Groq only when `users.ai_access = true`. Non-AI search embeds only the query and returns filenames plus similarity scores. Uploaded file content is embedded through HuggingFace in both modes because embedding is required for document search.

## Search Granularity

Each file is a single searchable unit. There is no chunking. This matches the requirement and keeps retrieval, deletion, and result presentation straightforward.

