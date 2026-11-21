FROM gradle:8.14-jdk21 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/global-chat-backend /app
EXPOSE 8080
CMD ["./bin/global-chat-backend"]