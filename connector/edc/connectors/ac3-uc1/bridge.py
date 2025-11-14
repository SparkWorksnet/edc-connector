import json
import os

import paho.mqtt.client as mqtt

# Configuration
MQTT_BROKER = os.getenv("MQTT_BROKER")
MQTT_PORT = int(os.getenv("MQTT_PORT"))
MQTT_TOPIC = os.getenv("MQTT_TOPIC")
OUTPUT_DIR = os.getenv("OUTPUT_DIR", "./data")  # Directory to save messages

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connected to MQTT broker")
        client.subscribe(MQTT_TOPIC)
    else:
        print(f"Connection failed with code {rc}")


def on_message(client, userdata, msg):
    dev_name = json.loads(msg.payload.decode("utf-8"))["topic"].split("/")[-1]
    file_path = os.path.join(OUTPUT_DIR, dev_name)

    with open(file_path, "w") as file:
        file.write(msg.payload.decode("utf-8"))

    print(f"Message received and saved to {file_path}")


# Setup MQTT client
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# Connect to the broker
client.connect(MQTT_BROKER, MQTT_PORT, 60)

# Start the loop
client.loop_forever()
