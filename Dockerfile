# --- Fase 1: compilar el proyecto con Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom, para que Docker guarde las dependencias descargadas
# y no las vuelva a bajar cada vez que cambia el código
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# --- Fase 2: imagen final, solo con Java y el .jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: si alguien encontrara un fallo en la app, no sería root
RUN useradd --system app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
