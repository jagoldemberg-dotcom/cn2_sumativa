# -------- Build stage --------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# -------- Runtime stage --------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/bff-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
