FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar target/overflow-0.0.1-SNAPSHOT.jar \
       extract --layers --destination target/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
WORKDIR /app

COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=orderflow:orderflow \
    /workspace/target/extracted/application/overflow-0.0.1-SNAPSHOT.jar ./application.jar

USER orderflow
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=6 \
    CMD wget --quiet --output-document=/dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
