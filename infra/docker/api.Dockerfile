# Backend — Dockerfile de desenvolvimento (não é o Dockerfile de produção).
#
# Build context: raiz do repositório (ver infra/docker-compose.dev.yml), por
# isso os caminhos de COPY abaixo começam em "apps/api/". Isso mantém este
# Dockerfile dentro de infra/**, evitando editar arquivos em apps/api/**.
#
# Multi-stage: build com Maven completo, runtime só com JRE (imagem final
# menor). Não usamos ./mvnw aqui de propósito — a imagem maven:3.9 já traz um
# Maven equivalente ao da wrapper (3.9.x), então evitamos o download da
# distribuição do Maven pela wrapper durante o build da imagem.
#
# Sem hot-reload dentro do container: isso exigiria spring-boot-devtools
# (dependência nova, fora do escopo deste ticket de dev-experience). Uma
# mudança no código do backend requer rebuild da imagem:
#   docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml \
#     up -d --build backend

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Camada de dependências separada do código-fonte para cache do Docker.
COPY apps/api/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

COPY apps/api/src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
