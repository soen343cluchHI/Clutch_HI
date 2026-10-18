FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY backend/src backend/src
RUN find backend/src -name '*.java' > sources.txt && \
    mkdir -p backend/out && \
    javac --release 17 --add-modules jdk.httpserver -d backend/out @sources.txt

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/out backend/out
COPY backend/config/live.properties backend/config/live.properties
EXPOSE 8080
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "backend/out", "sugarrush.presentation.http.ApiServer"]
