# syntax=docker/dockerfile:1

# Image chạy MediaService trong stack Docker tổng.
# Stage build dùng Maven Wrapper; image cuối chỉ chứa JRE và JAR.
# .env, target/ và file cấu hình bí mật không được đưa vào image.

FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Checkout trên Windows có thể khiến mvnw sử dụng CRLF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/

# Cache Maven dependencies giữa các lần build.
# Test chạy riêng; quá trình build image chỉ đóng gói ứng dụng.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -DskipTests package \
    && cp target/*.jar /workspace/app.jar


FROM eclipse-temurin:21-jre

RUN groupadd --system careconnect \
    && useradd --system \
        --gid careconnect \
        --no-create-home \
        careconnect

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

USER careconnect

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]