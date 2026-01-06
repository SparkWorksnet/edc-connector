#!/bin/sh
set -e

# Replace API_PORT placeholder in the HTML template with the actual environment variable value
envsubst '${API_PORT}' < /usr/share/nginx/html/dashboard.html.template > /usr/share/nginx/html/dashboard.html

echo "Dashboard configured with API_PORT: ${API_PORT}"

# Execute the CMD from Dockerfile (nginx)
exec "$@"