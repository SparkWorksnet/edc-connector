FROM nginx:alpine

# Install envsubst (already available in nginx:alpine via gettext package)
RUN apk add --no-cache gettext

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the dashboard HTML template
COPY src/dashboard.html /usr/share/nginx/html/dashboard.html.template

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
RUN dos2unix /entrypoint.sh

# Set default API port (can be overridden via environment variable)
ENV API_PORT=18191

# Expose port 80
EXPOSE 80

# Use custom entrypoint to substitute environment variables
ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
