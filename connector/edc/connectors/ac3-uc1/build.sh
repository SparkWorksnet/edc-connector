## build provider_hot
docker buildx build --platform linux/arm64/v8,linux/arm64,linux/amd64 . -f connector.dockerfile -t sparkworks/ac3-connector-iot-http-http-provider:latest --push
