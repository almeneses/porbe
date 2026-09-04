FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.61.0-noble
WORKDIR /app

COPY --from=build --chown=pwuser:pwuser /workspace/target/porbe-backend-*.jar app.jar

USER pwuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
