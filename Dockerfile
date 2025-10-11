# Use the official Gradle image to build the application
FROM gradle:8.5.0-jdk21 AS build

# Set the working directory
WORKDIR /home/gradle/src

# Copy the build files and source code
COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY src ./src

# Grant execute permission to the Gradle wrapper
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew build -x test

# Use a smaller base image for the final container
FROM eclipse-temurin:21-jre-jammy

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
