# DocVault

Full-stack semantic document search with optional AI summaries.

## Stack

- Java 17, Spring Boot 3, Spring Security, JWT
- PostgreSQL 15 with pgvector, Flyway, Spring Data JPA
- React 18, Vite, React Router, Axios, STOMP over SockJS
- HuggingFace Inference API embeddings
- Groq Llama 3 streaming summaries
- Docker Compose for Postgres and Redis infrastructure only

## Prerequisites on macOS

```bash
brew install openjdk@17 node@20 yarn maven
```

Install and start Docker Desktop.

## Configure Environment

Create your local environment from the example:

```bash
cp .env.example .env
```

Fill in:

```bash
POSTGRES_URL=jdbc:postgresql://127.0.0.1:15432/docvault
POSTGRES_USER=docvault
POSTGRES_PASSWORD=docvault
JWT_SECRET=<at least 32 bytes>
HF_API_TOKEN=<your HuggingFace token>
GROQ_API_KEY=<your Groq API key>
GROQ_MODEL=llama-3.1-8b-instant
```
A 256-bit JWT secret can be generated with:

```bash
openssl rand -base64 48
```

The backend reads environment variables from your shell. Export them before starting Spring Boot:

```bash
set -a
source ../.env
set +a
```

## Start Infrastructure

```bash
docker compose up -d
```

Postgres runs on `127.0.0.1:15432` with database `docvault`. Redis is exposed on `127.0.0.1:16379` but intentionally unused by the app per project decision. Docker binds both ports to loopback only, so they are not exposed on your LAN.

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

The API runs on `http://127.0.0.1:8080` and is bound to loopback only.

## Run Frontend

```bash
cd frontend
yarn
yarn dev
```

The UI runs on `http://127.0.0.1:5173` and is bound to loopback only. If Vite says it is using `5174` because `5173` is busy, open `http://127.0.0.1:5174/` instead.

## API Keys

HuggingFace:

1. Create a free account at [huggingface.co](https://huggingface.co).
2. Open Settings, then Access Tokens.
3. Create a token with inference access and set `HF_API_TOKEN`.

Groq:

1. Create a free account at [console.groq.com](https://console.groq.com).
2. Open API Keys.
3. Create a key and set `GROQ_API_KEY`.

## Useful Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`
- `PUT /api/users/ai-access`
- `POST /api/files/upload`
- `GET /api/files`
- `DELETE /api/files/{id}`
- `POST /api/search`

WebSocket endpoint: `/ws`

Topics:

- `/topic/upload-progress/{userId}`
- `/topic/search-results/{userId}`

## Frontend Build

```bash
cd frontend
yarn build
```
