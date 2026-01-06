# EDC Connector UI

A lightweight web-based dashboard for monitoring and managing Eclipse Dataspace Connector (EDC) instances.

## Features

- View Assets, Contract Definitions, Agreements, Negotiations, Transfers, and Policies
- Real-time data refresh (auto-updates every 10 seconds)
- Automatic API URL construction based on current hostname and configurable port
- Manual API URL override available via input field
- Responsive design
- Single-page HTML application

## Running with Docker

### Using Docker Compose (Recommended)

1. Build and run the container:
```bash
docker-compose up -d
```

2. Access the dashboard at http://localhost:8082

3. To use a different API port, edit the `API_PORT` environment variable in `docker-compose.yml`:
```yaml
environment:
  - API_PORT=9191
```

The dashboard automatically constructs the Management API URL based on the current hostname and the configured port.

### Using Docker CLI

1. Build the image:
```bash
docker build -t edc-connector-ui .
```

2. Run the container:
```bash
docker run -d \
  -p 8080:80 \
  -e API_PORT=18191 \
  --name edc-connector-ui \
  edc-connector-ui:latest
```

3. Access the dashboard at http://localhost:8080

The dashboard will automatically construct the API URL as `http://localhost:18191/management`.

## Configuration

### Environment Variables

- `API_PORT`: The port number of your EDC Management API (default: `18191`)

The dashboard automatically constructs the Management API URL using:
- Current page protocol (http/https)
- Current page hostname
- Configured API_PORT
- Path suffix: `/management`

**Result:** `{protocol}://{hostname}:{API_PORT}/management`

### Example Configurations

**For local development:**
```bash
docker run -d -p 8080:80 \
  -e API_PORT=18191 \
  edc-connector-ui
```
The dashboard will connect to: `http://localhost:18191/management`

**For custom port:**
```bash
docker run -d -p 8080:80 \
  -e API_PORT=9191 \
  edc-connector-ui
```
The dashboard will connect to: `http://localhost:9191/management`

**For Docker network (connector running in another container):**
```bash
docker run -d -p 8080:80 \
  -e API_PORT=18191 \
  --network edc-network \
  edc-connector-ui
```
Access the dashboard at http://localhost:8080 and manually update the API URL field if needed.

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
- Check that the API_PORT is configured correctly
- Verify the EDC connector is running and accessible at the expected port
- Check CORS settings on the EDC connector
- Check browser console for detailed error messages
- Manually verify the API URL in the dashboard input field

**Container fails to start:**
- Check that port 8080 (or 8082 for docker-compose) is not already in use
- Verify Docker is running properly
- Check container logs: `docker logs edc-connector-ui`

**API URL is incorrect:**
- The dashboard automatically constructs the API URL based on `window.location.hostname`
- If accessing via Docker network or proxy, you may need to manually update the API URL field in the dashboard
- You can always manually override the API URL using the input field at the top of the page

## License

Apache License 2.0