# EDC Connector UI

A lightweight web-based dashboard for monitoring and managing Eclipse Dataspace Connector (EDC) instances.

## Features

- View Assets, Contract Definitions, Agreements, Negotiations, Transfers, and Policies
- Real-time data refresh (auto-updates every 10 seconds)
- Configurable API endpoint
- Responsive design
- Single-page HTML application

## Running with Docker

### Using Docker Compose (Recommended)

1. Build and run the container:
```bash
docker-compose up -d
```

2. Access the dashboard at http://localhost:8080

3. To use a different API URL, edit the `API_URL` environment variable in `docker-compose.yml`:
```yaml
environment:
  - API_URL=http://your-edc-host:port/management
```

### Using Docker CLI

1. Build the image:
```bash
docker build -t edc-connector-ui .
```

2. Run the container:
```bash
docker run -d \
  -p 8080:80 \
  -e API_URL=http://localhost:18181/management \
  --name edc-connector-ui \
  edc-connector-ui:latest
```

3. Access the dashboard at http://localhost:8080

## Configuration

### Environment Variables

- `API_URL`: The URL of your EDC Management API (default: `http://localhost:18181/management`)

### Example Configurations

**For local development:**
```bash
docker run -d -p 8080:80 \
  -e API_URL=http://localhost:18181/management \
  edc-connector-ui
```

**For remote EDC connector:**
```bash
docker run -d -p 8080:80 \
  -e API_URL=https://edc.example.com/management \
  edc-connector-ui
```

**For Docker network (connector running in another container):**
```bash
docker run -d -p 8080:80 \
  -e API_URL=http://edc-connector:18181/management \
  --network edc-network \
  edc-connector-ui
```

## Project Structure

```
connector-ui/
├── src/
│   └── dashboard.html      # Main dashboard application
├── Dockerfile              # Docker image definition
├── docker-compose.yml      # Docker Compose configuration
├── entrypoint.sh          # Container entrypoint script
├── nginx.conf             # Nginx web server configuration
└── README.md              # This file
```

## Running Without Docker

Open `src/dashboard.html` directly in a web browser. You can manually change the API URL using the input field at the top of the page.

## Health Check

The container includes a health check endpoint at `/health` which returns a 200 status code when healthy.

## Security Considerations

- The dashboard makes client-side API calls to the EDC Management API
- Ensure proper CORS configuration on your EDC connector if the UI is hosted on a different domain
- Consider using HTTPS for production deployments
- The Management API should be properly secured with authentication

## API Compatibility

This dashboard is compatible with EDC Management API v3 and uses the following endpoints:

- `/v3/assets/request`
- `/v3/contractdefinitions/request`
- `/v3/contractagreements/request`
- `/v3/contractnegotiations/request`
- `/v3/transferprocesses/request`
- `/v3/policydefinitions/request`

## Troubleshooting

**Dashboard shows "Error: Failed to fetch":**
- Check that the API_URL is correct
- Verify the EDC connector is running and accessible
- Check CORS settings on the EDC connector
- Check browser console for detailed error messages

**Container fails to start:**
- Check that port 8080 is not already in use
- Verify Docker is running properly
- Check container logs: `docker logs edc-connector-ui`

## License

Apache License 2.0