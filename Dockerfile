# Jeden artefakt uruchomieniowy (M5.3/D30): front → jar → obraz z JRE.
# Trzy etapy, żeby w obrazie końcowym nie było ani Node, ani Mavena.

FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
# warstwa zależności osobno — zmiana kodu nie unieważnia pobranych artefaktów
RUN mvn -B -q dependency:go-offline
COPY src ./src
# front wchodzi gotowy z pierwszego etapu, więc build backendu nie potrzebuje Node
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
