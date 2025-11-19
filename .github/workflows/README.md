# GitHub Actions Workflows

## Deploy DALI Testbed Connector

**File:** `deploy-dali-testbed-connector.yml`

### Purpose
Builds and publishes the DALI Testbed Connector Docker image to GitHub Container Registry (ghcr.io).

### Trigger
- **Manual only** (`workflow_dispatch`) - Run on demand from the GitHub Actions UI

### What it does
1. Checks out the repository
2. Sets up Java 17
3. Builds the connector JAR using Gradle: `./gradlew :edc:connectors:dali-testbed-connector:shadowJar`
4. Builds Docker image using `connector.dockerfile` for `linux/amd64`
5. Pushes to GitHub Container Registry as `ghcr.io/<owner>/6gdali-testbed-connector`

### How to run
1. Go to the **Actions** tab in your GitHub repository
2. Select **Deploy DALI Testbed Connector** from the workflows list
3. Click **Run workflow** button
4. Select the branch you want to build from
5. Click **Run workflow**

### Output
The workflow publishes the following image tags:
- `ghcr.io/<owner>/6gdali-testbed-connector:latest` - Always points to the latest build
- `ghcr.io/<owner>/6gdali-testbed-connector:<branch>` - Tagged with the branch name
- `ghcr.io/<owner>/6gdali-testbed-connector:<branch>-<sha>` - Tagged with branch and commit SHA

### Pull the image
```bash
# Pull latest version
docker pull ghcr.io/<owner>/6gdali-testbed-connector:latest

# Pull specific version
docker pull ghcr.io/<owner>/6gdali-testbed-connector:main-abc1234
```

### Notes
- Requires `packages: write` permission (automatically granted to GITHUB_TOKEN)
- Image is built for `linux/amd64` platform only
- Uses GitHub Actions cache to speed up subsequent builds