
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system careconnect && useradd --system --gid careconnect --no-create-home careconnect
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER careconnect
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
