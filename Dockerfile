# Build stage
FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :application:installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/application/build/install/application .
EXPOSE 8080
CMD ["bin/application"]
