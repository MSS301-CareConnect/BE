# syntax=docker/dockerfile:1
# Image chạy CareRecipientService trong stack Docker tổng (../compose.yaml + compose.stack.yaml).
# Stage build dùng Maven Wrapper của service (đúng phiên bản Maven trong .mvn/); image cuối chỉ có JRE + JAR.
# .env, keys/ và target/ không vào build context (.dockerignore) — cấu hình và khóa cấp lúc chạy container.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Checkout trên Windows có thể đổi mvnw sang CRLF — chuẩn hóa để sh chạy được.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
# Cache ~/.m2 giữa các lần build; "locked" vì các service build song song dùng chung cache.
# Test chạy riêng bằng mvnw test — build image chỉ đóng gói.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system careconnect && useradd --system --gid careconnect --no-create-home careconnect
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER careconnect
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
