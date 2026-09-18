# syntax = docker/dockerfile:1.2

# Etapa 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiar archivos de configuración de Gradle y Wrapper
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Asegurar terminaciones de línea Unix y permisos de ejecución para gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Descargar dependencias para aprovechar la caché de capas de Docker
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fuente y compilar el archivo ejecutable (omitiendo tests)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Etapa 2: CDS Builder (Caché de Clases en Binario) -> PRECALENTAMIENTO DE RAM
RUN touch app.jsa && java -Dspring.context.exit=onRefresh -Dspring.main.lazy-initialization=true -XX:ArchiveClassesAtExit=app.jsa -jar build/libs/ChatBot-0.0.1-SNAPSHOT.jar || rm app.jsa || true

# Etapa 3: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Traer el jar empaquetado del build
COPY --from=build /app/build/libs/ChatBot-0.0.1-SNAPSHOT.jar app.jar
# Traer la caché JSA fabricada en la Etapa 2 si existe
COPY --from=build /app/app.js* ./

EXPOSE 8080

# ENTRYPOINT Inteligente que aprovecha CDS si app.jsa fue generado con éxito
ENTRYPOINT ["sh", "-c", "if [ -f app.jsa ]; then java -XX:SharedArchiveFile=app.jsa -jar app.jar; else java -jar app.jar; fi"]
