#!/bin/sh
set -e

# Replace API_URL placeholder in the HTML template with the actual environment variable value
envsubst '${API_URL}' < /usr/share/nginx/html/dashboard.html.template > /usr/share/nginx/html/dashboard.html

echo "Dashboard configured with API_URL: ${API_URL}"

# Execute the CMD from Dockerfile (nginx)
exec "$@"