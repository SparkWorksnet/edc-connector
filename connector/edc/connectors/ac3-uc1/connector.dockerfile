# Use an official OpenJDK runtime as a parent image
#FROM openjdk:17-jdk
FROM amazoncorretto:17-alpine-jdk

# Set the working directory inside the container
WORKDIR /usr/src/app

ENV MQTT_BROKER=localhost
ENV MQTT_PORT=1883
ENV MQTT_TOPIC=data/send

RUN apk add --update --no-cache python3 py3-pip
RUN pip3 install paho-mqtt --break-system-packages

# Copy the current directory contents into the container at /usr/src/app
COPY build/libs/connector.jar .
COPY bridge.py .

# Specify the command to run the application
CMD  java -jar -Dedc.fs.config=connector.properties connector.jar & \
	    python3 -u bridge.py &> bridge.log


# Define the mount point for the external directory
# This will be provided at runtime when the docker container is run using the -v flag
VOLUME /data
