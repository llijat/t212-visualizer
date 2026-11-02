# --- Stage 1: build the project with Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the pom first, so Docker caches the downloaded dependencies
# and does not fetch them again every time the code changes
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# --- Stage 2: final image, with only Java and the .jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Unprivileged user: a flaw in the app would not give anyone root
RUN useradd --system app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
