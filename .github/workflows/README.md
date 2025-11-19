# GitHub Actions Workflows

## 1. Deploy DALI Testbed Connector

**File:** `deploy-dali-testbed-connector.yml`

### Purpose
Builds and publishes the DALI Testbed Connector Docker image to GitHub Container Registry (ghcr.io).

### Trigger
- **Manual only** (`workflow_dispatch`) - Run on demand from the GitHub Actions UI

### What it does
1. Checks out the repository
2. Sets up Java 17 and Gradle
3. Builds the connector JAR using Gradle: `./gradlew :edc:assemble`
4. Builds Docker image using `connector.dockerfile` for `linux/amd64`
5. Pushes to GitHub Container Registry as `ghcr.io/<owner>/6gdali-testbed-connector`

### How to run
1. Go to the **Actions** tab in your GitHub repository
2. Select **Deploy DALI Testbed Connector** from the workflows list
3. Click **Run workflow** button
4. Select the branch you want to build from
5. Click **Run workflow**

### Output
Image tags:
- `ghcr.io/<owner>/6gdali-testbed-connector:latest`
- `ghcr.io/<owner>/6gdali-testbed-connector:<branch>`
- `ghcr.io/<owner>/6gdali-testbed-connector:<branch>-<sha>`

### Usage
```bash
# Pull the image
docker pull ghcr.io/<owner>/6gdali-testbed-connector:latest

# Run the connector
docker run -d \
  -v /path/to/data:/data \
  -e EDC_FS_CONFIG=connector.properties \
  ghcr.io/<owner>/6gdali-testbed-connector:latest
```

---

## 2. Deploy Connector UI

**File:** `deploy-connector-ui.yml`

### Purpose
Builds and publishes the EDC Connector Web UI Docker image to GitHub Container Registry (ghcr.io).

### Trigger
- **Manual only** (`workflow_dispatch`) - Run on demand from the GitHub Actions UI

### What it does
1. Checks out the repository
2. Builds Docker image from `connector-ui/Dockerfile` for `linux/amd64`
3. Pushes to GitHub Container Registry as `ghcr.io/<owner>/connector-ui`

### How to run
1. Go to the **Actions** tab in your GitHub repository
2. Select **Deploy Connector UI** from the workflows list
3. Click **Run workflow** button
4. Select the branch you want to build from
5. Click **Run workflow**

### Output
Image tags:
- `ghcr.io/<owner>/connector-ui:latest`
- `ghcr.io/<owner>/connector-ui:<branch>`
- `ghcr.io/<owner>/connector-ui:<branch>-<sha>`
- `ghcr.io/<owner>/connector-ui:<version>` (if tagged with semver)

### Usage
```bash
# Pull the image
docker pull ghcr.io/<owner>/connector-ui:latest

# Run the UI
docker run -d \
  -p 8080:80 \
  -e API_URL=http://localhost:18181/management \
  ghcr.io/<owner>/connector-ui:latest

# Access at http://localhost:8080
```

---

## Notes

### Common to Both Workflows
- Requires `packages: write` permission (automatically granted to GITHUB_TOKEN)
- Images are built for `linux/amd64` platform only
- Uses GitHub Actions cache to speed up subsequent builds
- Automatically generates build summaries with pull/run commands

### Multi-Platform Support
To build for additional platforms (e.g., ARM), modify the `platforms` field:
```yaml
platforms: linux/amd64,linux/arm64,linux/arm/v7
```

### Accessing Published Images
Published images are available at:
- https://github.com/orgs/YOUR_ORG/packages