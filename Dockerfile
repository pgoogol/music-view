# Obraz backendu (M5.3/D30) — sama aplikacja Spring Boot. Front jedzie osobnym
# obrazem (frontend/Dockerfile) i osobną usługą: dwie aplikacje, nie jedna.
# Dwa etapy, żeby w obrazie końcowym nie było Mavena.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
# warstwa zależności osobno — zmiana kodu nie unieważnia pobranych artefaktów
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
