FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
