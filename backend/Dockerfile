FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.61.0-noble
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && mkdir -p /opt/codex /home/pwuser/.codex \
    && curl -fsSL https://chatgpt.com/codex/install.sh \
        | CODEX_HOME=/opt/codex CODEX_INSTALL_DIR=/usr/local/bin CODEX_NON_INTERACTIVE=1 sh \
    && chown -R pwuser:pwuser /home/pwuser/.codex \
    && rm -rf /var/lib/apt/lists/*

ENV CODEX_HOME=/home/pwuser/.codex
WORKDIR /app

COPY --from=build --chown=pwuser:pwuser /workspace/target/porbe-backend-*.jar app.jar

USER pwuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
