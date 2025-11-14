docker buildx build --platform linux/arm/v7,linux/arm64/v8,linux/amd64 . -f Dockerfile -t sparkworks/connector-ui:0.1 --push
