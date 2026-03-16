FROM mwader/static-ffmpeg:8.0.1 AS ffmpeg-source

# --- Runtime Stage ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=ffmpeg-source /ffmpeg /usr/local/bin/
COPY --from=ffmpeg-source /ffprobe /usr/local/bin/

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
