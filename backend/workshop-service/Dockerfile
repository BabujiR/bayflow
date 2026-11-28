# Build context: ./backend   (docker build -f workshop-service/Dockerfile backend)

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl workshop-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-alpine
# Security fixes for Alpine packages released since the base image was built (see APK_REFRESH
# in .github/workflows/cd.yml: a new value each run, so the build cache never keeps old packages).
ARG APK_REFRESH=0
RUN echo "packages refreshed: ${APK_REFRESH}" && apk upgrade --no-cache
RUN addgroup -S bayflow && adduser -S -G bayflow bayflow
WORKDIR /app
COPY --from=build /workspace/workshop-service/target/workshop-service.jar app.jar
USER bayflow
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8082/actuator/health/readiness || exit 1
# Memory in a container (mem_limit): 60 % for the heap leaves room for class metadata, compiled
# code and thread stacks; compact object headers (Java 25) make every object smaller. A JVM out of
# memory exits so Docker restarts it, instead of limping on. To change them, set JAVA_TOOL_OPTIONS
# on the container (it replaces this whole list).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=192m -XX:+UseCompactObjectHeaders -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
