# Build context: ./backend   (docker build -f auth-service/Dockerfile backend)

FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl auth-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bayflow && adduser -S -G bayflow bayflow
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service.jar app.jar
USER bayflow
EXPOSE 8083
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8083/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
