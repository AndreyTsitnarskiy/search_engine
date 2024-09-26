# Start with a base image containing Java runtime
FROM openjdk:17-jdk-slim
LABEL authors = "Hood"

# Set the working directory inside the container
WORKDIR /app

# Copy the application JAR file into the container
COPY target/SearchEngine-1.0-SNAPSHOT.jar /app/SearchEngine-1.0-SNAPSHOT.jar

# Mount external application.yml into the config directory
#COPY ../application.yaml /config/application.yml

# Expose the port your application will run on
EXPOSE 10100

# Set the entry point for the container
ENTRYPOINT ["java", "-jar", "/app/SearchEngine-1.0-SNAPSHOT.jar", "--spring.config.location=file:/config/application.yml"]