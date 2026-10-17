FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -ntp -pl backend -am dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -ntp -pl backend -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 --create-home appuser
WORKDIR /app
RUN chown appuser /app
COPY --from=build --chown=appuser /build/backend/target/backend-1.0.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
