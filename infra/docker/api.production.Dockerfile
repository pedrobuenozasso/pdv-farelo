FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/api/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline
COPY apps/api/src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S farelo && adduser -S farelo -G farelo
COPY --from=build --chown=farelo:farelo /build/target/*.jar /app/app.jar
USER farelo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
