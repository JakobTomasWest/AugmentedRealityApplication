# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# Make wrapper executable inside the container
RUN chmod +x ./gradlew
# Build a fat jar
RUN ./gradlew --no-daemon clean shadowJar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the fat jar produced by shadowJar (ends with -all.jar)
COPY --from=build /app/build/libs/*-all.jar /app/app.jar

# Where uploads will be stored (we'll mount a volume here)
RUN mkdir -p /app/uploads

# Ktor default port
EXPOSE 8080
ENV PORT=8080

# Configurable upload root; Compose will override this
ENV UPLOAD_ROOT=/app/uploads
# Dev default for JWT secret; Compose will override this too
ENV SECRET=dev-super-secret

CMD ["java", "-jar", "/app/app.jar"]