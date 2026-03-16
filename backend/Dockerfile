# Get the exact static FFmpeg version ---
# mwader provides highly reliable, statically compiled FFmpeg binaries
FROM mwader/static-ffmpeg:8.0.1 AS ffmpeg-source

# --- Runtime Stage ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy ffmpeg directly from the static image
COPY --from=ffmpeg-source /ffmpeg /usr/local/bin/

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# 1. Capture the port from the build command/docker-compose
ARG SERVICE_PORT
# 2. Set it as an Environment Variable so Spring Boot can see it
ENV PORT=${SERVICE_PORT}

COPY target/*.jar app.jar

# 3. Inform Docker which port to watch (uses the variable)
EXPOSE ${PORT}

# 4. Tell Spring Boot to use this port specifically
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
