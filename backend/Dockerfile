FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package && cp target/weather-data-hub-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl ffmpeg
COPY --from=build /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
