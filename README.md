# EDC Connector - SparkWorks

Custom Eclipse Dataspace Connector (EDC) implementation with enhanced capabilities and monitoring dashboard.

## Overview

This repository contains:

1. **Custom EDC Connector** - Extended EDC connector with custom extensions for specific use cases
2. **Web Dashboard** - Lightweight monitoring interface for EDC management

## Repository Structure

```
├── connector/                    # Custom EDC Connector (Java/Gradle)
│   └── edc/
│       ├── extensions/          # Custom EDC extensions
│       │   ├── transfer-recovery/
│       │   ├── http-streaming-datasource/
│       │   └── http-data-sink/
│       └── connectors/          # Connector implementations
│           └── ac3-uc1/        # AC3 Use Case 1 connector
│
└── connector-ui/                # Web-based dashboard (HTML/Docker)
    ├── src/
    │   └── dashboard.html
    ├── Dockerfile
    └── docker-compose.yml
```

## Prerequisites

### For Connector
- Java 17 or higher
- Gradle 7.x or higher (wrapper included)

### For Dashboard
- Docker and Docker Compose (for containerized deployment)
- Or any modern web browser (for standalone HTML)

## Quick Start

### Running the Connector

Navigate to the connector directory and build:

```bash
cd connector
./gradlew build
```

Run tests:
```bash
./gradlew test
```

Run a specific connector (example):
```bash
./gradlew :edc:connectors:ac3-uc1:build
```

### Running the Dashboard

#### Using Docker Compose (Recommended)

```bash
cd connector-ui
docker-compose up -d
```

Access the dashboard at http://localhost:8080

#### Using Docker CLI

```bash
cd connector-ui
docker build -t edc-connector-ui .
docker run -d -p 8080:80 -e API_URL=http://localhost:18181/management edc-connector-ui
```

#### Standalone (No Docker)

Open `connector-ui/src/dashboard.html` directly in a web browser.

## Custom Extensions

### Transfer Recovery Extension
Provides enhanced transfer recovery capabilities with automatic retry and failure handling mechanisms.

### HTTP Streaming Datasource Extension
Enables streaming data sources over HTTP for efficient large-file transfers.

### HTTP Data Sink Extension
Implements HTTP-based data sink for receiving transferred data.

## Connector Implementations

### AC3-UC1 Connector
Pre-configured connector implementation for AC3 Use Case 1, including:
- Custom policy configurations
- Specific protocol implementations
- Use case-specific asset management

## Dashboard Features

The web dashboard provides real-time monitoring and management:

- **Assets Management** - View and monitor data assets
- **Contract Definitions** - Track contract offerings
- **Contract Agreements** - Monitor active agreements
- **Contract Negotiations** - Track negotiation status
- **Transfer Processes** - Monitor data transfers
- **Policy Definitions** - View policy configurations

**Key Features:**
- Auto-refresh every 10 seconds
- Configurable API endpoint
- Responsive design
- RESTful API integration with EDC Management API v3

## Configuration

### Connector Configuration

Configure the connector using standard EDC configuration approaches:

1. **System Properties**: `-Dedc.property.name=value`
2. **Environment Variables**: `EDC_PROPERTY_NAME=value`
3. **Configuration Files**: Place in resources directory

Refer to [Eclipse EDC documentation](https://eclipse-edc.github.io) for detailed configuration options.

### Dashboard Configuration

Configure via environment variable:

```bash
export API_URL=http://your-edc-host:port/management
```

Or in `docker-compose.yml`:
```yaml
environment:
  - API_URL=http://edc-connector:18181/management
```

## Development

### Building from Source

#### Connector
```bash
cd connector
./gradlew clean build
```

#### Dashboard
```bash
cd connector-ui
./build.sh  # Builds Docker image
```

### Running Tests

```bash
cd connector
./gradlew test
```

For specific test categories:
```bash
./gradlew test --tests "ClassName"
```

### Code Quality

The project uses Checkstyle for code quality enforcement:

```bash
./gradlew checkstyleMain
```

Configuration files:
- `connector/resources/checkstyle-config.xml`
- `connector/resources/suppressions.xml`

## API Compatibility

- **EDC Version**: Compatible with EDC 0.15.0-SNAPSHOT
- **Management API**: v3
- **DSP Protocol**: Supports multiple versions (0.8, 2024/1, 2025/1)

## Docker Deployment

### Complete Stack Deployment

To deploy both connector and dashboard together, create a custom `docker-compose.yml`:

```yaml
version: '3.8'

services:
  edc-connector:
    image: your-edc-connector:latest
    ports:
      - "18181:18181"  # Management API
      - "19191:19191"  # DSP Protocol
    environment:
      - EDC_CONNECTOR_NAME=provider
      # Add your connector configuration

  edc-dashboard:
    image: edc-connector-ui:latest
    ports:
      - "8080:80"
    environment:
      - API_URL=http://edc-connector:18181/management
    depends_on:
      - edc-connector
```

## Troubleshooting

### Connector Issues

**Build failures:**
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

**Test failures:**
```bash
# Run with detailed output
./gradlew test --info
```

### Dashboard Issues

**Cannot connect to API:**
- Verify the EDC connector is running
- Check the API_URL configuration
- Ensure CORS is properly configured on the EDC connector
- Check network connectivity between dashboard and connector

**Dashboard not loading:**
```bash
# Check container logs
docker logs edc-connector-ui

# Verify container is running
docker ps | grep edc-connector-ui
```

## Security Considerations

- Secure the EDC Management API with proper authentication
- Use HTTPS for production deployments
- Configure CORS appropriately for cross-origin requests
- Review and apply security best practices from [EDC Security Documentation](https://eclipse-edc.github.io)
- Never expose management APIs directly to the internet without authentication

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

Ensure all tests pass and code follows the Checkstyle rules before submitting.

## Resources

- [Eclipse EDC Documentation](https://eclipse-edc.github.io)
- [EDC GitHub Repository](https://github.com/eclipse-edc/Connector)
- [EDC Community Discord](https://discord.gg/n4sD9qtjMQ)
- [Dataspace Protocol Specification](https://docs.internationaldataspaces.org/)

## License

Apache License 2.0

---

For detailed information about the dashboard component, see [connector-ui/README.md](connector-ui/README.md).