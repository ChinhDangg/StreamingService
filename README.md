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

## Core technologies

- **Java 25 / Spring Boot 3.5.16**
- **MinIO** (S3-compatible object storage)
- **PostgreSQL** (metadata & relational data)
- **MongoDB** (file structure & metadata)
- **Redis** (streams, caching, coordination)
- **Kafka ** (event backbone)
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
├── frontend/ # Spring MVC / Thymeleaf web UI
├── file-service/ # upload, maintain file structure and file metadata to managed all file upload
├── media-handler/ # Handle media mutation API and media entry 
├── workers/ # send transcoding jobs, Background FFmpeg workers for videos, albums and thumbnails 
├── search-service/ # call searching and OpenSearch indexer
├── media-backup/ # Optional file backup jobs
├── media-persistence/ # Shared JPA entities & repositories 
├── search-client/ # OpenSearch client abstraction 
└── common/ # Shared DTOs, enums, utilities 
</pre>

### Request flow (simplified)

1. Client requests a page, API, or stream
2. **OpenResty** validates only static access and routes the request
3. **Spring services** handle business logic
4. **Workers** generate or serve media asynchronously
5. **Events** propagate changes via Kafka
6. **Search index** is updated asynchronously
Media objects are **never exposed directly**—all access flows through OpenResty.

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