FROM eclipse-temurin:25.0.4_7-jdk-noble AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon \
    && cp build/libs/app.jar application.jar \
    && java -Djarmode=tools -jar application.jar \
        extract --layers --destination extracted

FROM eclipse-temurin:25.0.4_7-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 focuson \
    && useradd --uid 10001 --gid 10001 --no-create-home \
        --home-dir /application --shell /usr/sbin/nologin focuson

WORKDIR /application

COPY --from=builder --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent --show-error \
        http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
