# --- Runtime Stage ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
