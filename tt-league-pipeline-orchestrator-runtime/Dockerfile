# Pipeline orchestrator runtime image (FEAT-00116).
#
# The Boot jar is built on the host, because the org.albertsanso:* SNAPSHOT dependencies exist only in the local
# Maven repository. Build the jar first, then build the image with this module directory as the context:
#   mvn -pl tt-league-pipeline-orchestrator-runtime -am clean package -DskipTests
#   docker build -t tt-league/orchestrator-runtime tt-league-pipeline-orchestrator-runtime
ARG JAR_FILE=target/tt-league-pipeline-orchestrator-runtime-0.0.1-SNAPSHOT.jar

FROM eclipse-temurin:21-jre AS extract
ARG JAR_FILE
WORKDIR /build
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre AS runtime
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="tt-league-pipeline-orchestrator-runtime" \
      org.opencontainers.image.source="https://github.com/albertsanso/tt-league-services" \
      org.opencontainers.image.revision="${GIT_SHA}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /var/lib/tt-pipeline/artifacts \
    && chown -R 10001:10001 /var/lib/tt-pipeline

WORKDIR /app
COPY --from=extract /layers/dependencies/ ./
COPY --from=extract /layers/spring-boot-loader/ ./
COPY --from=extract /layers/snapshot-dependencies/ ./
COPY --from=extract /layers/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    PIPELINE_ARTIFACTS_DIR=/var/lib/tt-pipeline/artifacts

USER 10001:10001
EXPOSE 8095
VOLUME ["/var/lib/tt-pipeline/artifacts"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8095/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
