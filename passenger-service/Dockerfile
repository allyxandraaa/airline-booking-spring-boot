FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean build -x test --no-daemon
RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system --gid 10001 spring \
    && adduser --system --uid 10001 --ingroup spring spring

COPY --chown=spring:spring --from=builder /workspace/extracted/dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/application/ ./

USER 10001:10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
