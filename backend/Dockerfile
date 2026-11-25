FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
ARG OPERATOR_TOOL_IMAGE=false
RUN if [ "$OPERATOR_TOOL_IMAGE" = "true" ]; then mvn -B -ntp -DskipTests package; else mvn -B -ntp verify; fi
RUN mkdir /runtime && cd /runtime && jar -xf /workspace/infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-spa \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app app
RUN mkdir -p /var/lib/dependency-intelligence/repositories /var/lib/dependency-intelligence/documents && chown -R app:app /var/lib/dependency-intelligence
WORKDIR /app
# Tika's worker JVM needs real classpath entries outside the Spring Boot archive.
COPY --from=build /runtime/BOOT-INF/classes /app/classes
COPY --from=build /runtime/BOOT-INF/lib /app/lib
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/classes:/app/lib/*", "com.dependencyintelligence.DependencyIntelligenceApplication"]
