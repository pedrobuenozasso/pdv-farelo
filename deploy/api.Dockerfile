FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/api/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY apps/api/src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S farelo && adduser -S farelo -G farelo
WORKDIR /app
COPY --from=build --chown=farelo:farelo /build/target/*.jar app.jar
USER farelo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
