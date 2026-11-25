FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && mkdir /data && chown app:app /data
COPY --from=build /src/target/rental-engine.jar app.jar
USER app
ENV PORT=8081 DATA_DIR=/data
VOLUME /data
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://127.0.0.1:8081/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
