# Use an official OpenJDK runtime as a parent image
#FROM openjdk:17-jdk
FROM amazoncorretto:17-alpine-jdk

# Set the working directory inside the container
WORKDIR /usr/src/app

# Copy the current directory contents into the container at /usr/src/app
COPY build/libs/connector.jar .

# Specify the command to run the application
CMD  java -jar -Dedc.fs.config=connector.properties connector.jar


# Define the mount point for the external directory
# This will be provided at runtime when the docker container is run using the -v flag
VOLUME /data
