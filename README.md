# StreamingService

**StreamingService** is a modular, self-hosted media streaming platform built with **Spring Boot**.  
It supports authenticated media upload, on-demand and cached HLS streaming, background transcoding, full-text search, and both server-rendered and single-page web UIs.

The platform is designed to serve **media assets (videos and images) at multiple resolutions**.  
When a requested resolution does not already exist, the system performs **live transcoding** from the original stored media, generating **HLS chunks in real time**, storing them **in memory (RAM)**, and **streaming them immediately to the user** without writing intermediate data to disk.

The system is built around **clear service responsibilities**, **event-driven workflows**, and **high-performance media delivery** using RAM-backed storage and an **nginx/OpenResty edge** for secure, low-latency streaming.


---

## Core technologies

- **Java 25 / Spring Boot 3.5.x**
- **MinIO** (S3-compatible object storage)
- **PostgreSQL** (metadata & relational data)
- **Redis** (streams, caching, coordination)
- **Kafka / Redpanda** (event backbone)
- **OpenSearch** (search & indexing)
- **FFmpeg** (HLS + thumbnails)
- **OpenResty (nginx + Lua)** (edge proxy & streaming gateway)
- **Docker + Docker Compose**

---

## Repository layout

<pre>StreamingService 
├── auth-service/ # Authentication & JWT issuing 
├── backend/ # Core API + infra bootstrap + orchestration 
├── frontend/ # Spring MVC / Thymeleaf web UI 
├── media-upload/ # Upload & mutation API 
├── workers/ # Background FFmpeg workers 
├── search-indexer/ # Kafka → OpenSearch indexer 
├── media-object/ # MinIO object-event processing 
├── media-backup/ # Optional MinIO backup jobs 
├── media-persistence/ # Shared JPA entities & repositories 
├── search-client/ # OpenSearch client abstraction 
└── common/ # Shared DTOs, enums, utilities 
</pre>


Each directory (except shared modules) is a **standalone Spring Boot application**.

---

## High-level architecture

### Request flow (simplified)

1. Client requests a page, API, or stream
2. **OpenResty** validates access and routes the request
3. **Spring services** handle business logic
4. **Workers** generate or serve media asynchronously
5. **Events** propagate changes via Kafka
6. **Search index** is updated asynchronously

Media objects are **never exposed directly**—all access flows through OpenResty.


## Environment variables & configuration ownership

All services in this repository **share a single environment configuration**, which is
**owned by the `backend` module**.

### Key rule

> **Every service (auth-service, frontend, workers, media-upload, search-indexer, etc.)
> expects its environment variables to be defined in the `backend` module.**

No service maintains its own `.env` file.

---

### Why this design exists

- The `backend` service is responsible for:
    - Detecting the host OS
    - Creating and mounting the RAM disk
    - Selecting the correct Docker Compose file
    - Starting Docker infrastructure
- Centralizing environment variables avoids:
    - Duplicate configuration
    - Drift between services
    - Port mismatches and secret inconsistencies

---

### How it works in practice

1. Environment variables are defined **once**, in the backend module:
    - via exported shell variables, or
    - via a `.env` file located in `backend/`
2. `backend` starts Docker Compose using these variables
3. **All other Spring Boot services inherit the same environment**
4. Each service reads only the variables it needs from that shared set

---

### What NOT to do

- ❌ Do not create `.env` files in individual service directories
- ❌ Do not redefine ports or secrets per service
- ❌ Do not start services before backend is running

---

### When adding a new service

If you add a new service:
1. Define its required environment variables in the backend environment
2. Reference them in that service’s `application.properties`
3. Do **not** introduce a separate env file


---

## Service responsibilities

### `backend` (core coordinator)

The **backend** is the central brain of the system.

**Responsibilities:**
- Acts as the **primary API** for:
    - Media metadata
    - Albums
    - Search queries
- Coordinates **transcoding jobs**
    - Decides when a video needs HLS generation
    - Dispatches jobs to workers via Redis/Kafka
- Performs **search queries**
    - Uses `search-client` to query OpenSearch
    - Falls back to the database when appropriate
- Hosts infrastructure bootstrap logic
    - Detects host OS (Linux / macOS / Windows)
    - Creates and mounts a RAM disk or tmpfs
    - Starts the correct Docker Compose file
- Owns nginx/OpenResty configuration

⚠️ **Backend must be started first**.

---

### `auth-service`

Handles **authentication and token lifecycle**.

**Responsibilities:**
- Login and logout
- Access token issuance (JWT)
- Refresh token rotation
- JWKS endpoint for public key distribution

Other services **never issue tokens**—they only validate them.

---

### `frontend`

Server-side rendered web UI.

**Responsibilities:**
- Page routing (`/page/**`)
- Thymeleaf views
- Tailwind-based styling
- HLS playback helpers (Hls.js)
- Album and media browsing UI

The frontend does **not** talk directly to storage or workers.

---

### `media-upload`

Handles **media ingestion and mutation**.

**Responsibilities:**
- File uploads (media + thumbnails)
- Validation and metadata persistence
- Emitting Kafka events for:
    - New media
    - Updates
    - Deletions
- Triggering thumbnail generation jobs

This service does **not** serve media.

---

### `workers`

Background processing layer.

**Responsibilities:**
- Run FFmpeg jobs:
    - HLS playlists
    - Multi-resolution segments
    - Thumbnails
- Write segments to a shared **RAM-backed `/chunks` volume**
- Track job state and retries via Redis Streams
- Cleanup unused or stale HLS data
- Enforce concurrency limits

Workers never accept HTTP traffic.

---

### `search-indexer`

Asynchronous indexing service.

**Responsibilities:**
- Consume Kafka events emitted by backend and media-upload
- Transform entities into OpenSearch documents
- Keep search indexes eventually consistent
- Never block user-facing requests

Backend does **not** index synchronously.

---

### `media-object`

MinIO object event processor.

**Responsibilities:**
- React to MinIO bucket notifications
- Perform object-level side effects
- Emit additional events if required

---

### `media-backup` (optional)

Background backup service.

**Responsibilities:**
- Periodic or event-driven MinIO backups
- External filesystem or cold storage targets
- Disabled by default

---

### Shared modules

#### `media-persistence`
- JPA entities
- Repositories
- Database mappings

#### `search-client`
- OpenSearch client abstraction
- Query builders and mappings

#### `common`
- Shared DTOs
- Enums
- Utilities

---

## Streaming design

- HLS is generated **on demand**
- Segments are stored in **RAM**, not disk
- OpenResty serves `/stream/**` with:
    - Authentication
    - Rate limiting
    - Token validation
- Streams may originate from:
    - RAM (live transcoding)
    - MinIO (cached output)

This avoids persistent disk I/O and enables fast startup.

---

## Prerequisites

- Java 25
- Docker + Docker Compose
- Node.js / npm (frontend assets)
- Adequate RAM (RAM disk is heavily used)

---

## Local development (required startup order)

### 1️⃣ Start Docker

Docker **must be running** before anything else.

---

### 2️⃣ Start `backend` FIRST (mandatory)

Backend performs infrastructure bootstrap.

**What it does:**
1. Detects your OS
2. Creates a RAM disk or tmpfs
3. Selects the correct compose file
4. Starts Docker Compose automatically
