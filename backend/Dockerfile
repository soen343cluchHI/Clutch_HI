FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src src
RUN find src -name '*.java' > sources.txt && \
    mkdir -p out && \
    javac --release 17 --add-modules jdk.httpserver -d out @sources.txt

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out out
COPY config/app.properties config/app.properties
COPY demo/seed.json demo/seed.json
EXPOSE 8080
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "out", "sugarrush.presentation.http.ApiServer", "config/app.properties"]
