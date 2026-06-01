FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle* settings.gradle* ./
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -r -u 10001 appuser

COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

# Dieses Image IST das Produktions-Artefakt — ohne aktives Profil hätte die App
# keinen Datasource (steht nur in application-prod.yaml) und der
# ProductionSecretsValidator liefe nicht. Default daher auf prod; zur Laufzeit
# via `-e SPRING_PROFILES_ACTIVE=...` überschreibbar.
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
