# Multi-stage build for HireMate AI
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY data ./data
RUN mvn clean package -DskipTests

# Lean runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/hiremate-ai-1.0.0-SNAPSHOT.jar app.jar
COPY --from=build /app/data ./data

EXPOSE 8080
ENV PORT=8080
ENV GEMINI_MODEL=gemini-3.8-flash
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]

