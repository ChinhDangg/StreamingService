# StreamingService

**StreamingService** is a modular, self-hosted media streaming platform built with **Spring Boot**.  
It supports authenticated media upload, on-demand and cached HLS streaming, background transcoding, full-text search, and both server-rendered and single-page web UIs.

The platform is designed to serve **media assets (videos and images) at multiple resolutions at runtime rather than pre-generating them and storing them on disk to save space**.  
When a requested resolution does not already exist, the system performs **live transcoding** from the original stored media, generating **HLS chunks in real time**, storing them **in memory (RAM)**, and **streaming them immediately to the user** without writing intermediate data to disk.

The system is built around **clear service responsibilities**, **event-driven workflows**, and **high-performance media delivery** using RAM-backed storage and an **nginx/OpenResty edge** for secure, low-latency streaming.

Support a filesystem structure similar to a traditional file server like Google Drive over the web to organize your media files. Pick videos to put them in the streaming service to browse and add tags for searching. Pick group of images or videos to create an album and view them 
in a single scrollable page like a photo gallery or comic webcomic. Group of albums can be put together to become a grouper - to share common tags and get searched together like a multiple chapters comic.

---

## Reqiurements
- Java 25
- Docker running

---

To run the project, have java 25 installed and docker running. Download the project. 
- To start without any configuration, run the startup.sh script in ./z-prod-general.
- To start with a custom configuration, edit the .env file or even the docker-compose.yml file.
- Future update may create a single docker compose file for all services that pulls in all the necessary services.
So user can just run docker compose up on a public url with default configuration
---

Read below for the stack and architecture details and how to run the project locally to update the code.

---

## Core technologies

- **Java 25 / Spring Boot 3.5.9**
- **MinIO** (S3-compatible object storage)
- **PostgreSQL** (metadata & relational data)
- **MongoDB** (file structure & metadata)
- **Redis** (streams, caching, coordination)
- **Kafka / Redpanda** (event backbone)
- **OpenSearch** (search & indexing)
- **FFmpeg** (HLS + thumbnails)
- **OpenResty (nginx + Lua)** (edge proxy & streaming gateway & rate limiting & edge auth for some static assets)
- **Docker + Docker Compose**

---

## Prerequisites

- Java 25
- Docker + Docker Compose
- Node.js / npm (frontend assets)
- Adequate RAM (RAM disk is heavily used if transcoding is enabled)

---


## Repository layout

<pre>StreamingService 
├── auth-service/ # Authentication & JWT issuing 
├── backend/ # Core API + orchestration (call searching and send transcoding jobs)
├── frontend/ # Spring MVC / Thymeleaf web UI
├── file-service/ # handle allowed upload, maintain file structure and file metadata to managed all file upload
├── media-upload/ # Upload & mutation API 
├── workers/ # Background FFmpeg workers for videos, albums and thumbnails 
├── search-indexer/ # OpenSearch indexer 
├── media-object/ # MinIO object-event processing 
├── media-backup/ # Optional file backup jobs
├── media-persistence/ # Shared JPA entities & repositories 
├── search-client/ # OpenSearch client abstraction 
└── common/ # Shared DTOs, enums, utilities 
</pre>


Each directory (except shared modules) is a **standalone Spring Boot application**.
All services are **self-contained** and can be run independently.
All services use the .env file to configure their behavior.
- For development, to use host RAMDISK (only for MACOS and Linux), create the RAMDISK and set the name in .env file. 
Start docker compose to get all data services running. Then start all logic services.
- The `start-dev-mac.sh` script is a convenience script to do all of the above.
- 

---

## High-level architecture

### Request flow (simplified)

1. Client requests a page, API, or stream
2. **OpenResty** validates only static access and routes the request
3. **Spring services** handle business logic
4. **Workers** generate or serve media asynchronously
5. **Events** propagate changes via Kafka
6. **Search index** is updated asynchronously

Media objects are **never exposed directly**—all access flows through OpenResty.


---

## Service responsibilities

### `backend`

The **backend** is the main reading - search and decide transcoding jobs.

**Responsibilities:**
- Acts as the **primary API** for:
    - Media metadata
    - Albums
    - Search queries
- Coordinates **transcoding jobs**
    - Decides when a video needs HLS generation
    - Dispatches jobs to workers via Redis
- Performs **search queries**
    - Uses `search-client` to query OpenSearch
    - Falls back to the database when appropriate

---

### `auth-service`

Handles **authentication and token lifecycle**.

**Responsibilities:**
- Login and logout
- Access token issuance (JWT)
- Refresh token rotation
- JWKS endpoint for public key distribution
- Other services can **authenticate** using the public key

Other services **never issue tokens**—they only validate them.

---

### `file-service`

Handles **Initiate file upload and maintain file structure and file metadata**.

**Responsibilities:**
- Allow what file to upload or what folder to create
- Maintain file structure and file metadata

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