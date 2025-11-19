# 6GDALI EDC Scripts

Scripts for managing EDC assets and configurations for the 6GDALI project.

## Setup

### 1. Install Dependencies

```bash
pip install python-dotenv
```

### 2. Configure Environment Variables

Copy the example environment file and fill in your credentials:

```bash
cp .env.example .env
```

Edit `.env` and update the following values:

- `PROVIDER_DOMAIN` - Your EDC provider domain
- `MINIO_ACCESS_KEY` - Your MinIO access key
- `MINIO_SECRET_KEY` - Your MinIO secret key
- `MINIO_BUCKET_NAME` - Your MinIO bucket name
- `ASSET_NAME` - The name for your asset

**Important:** The `.env` file contains sensitive credentials and should NEVER be committed to git. It is already excluded in `.gitignore`.

### 3. Verify Configuration

The `.env` file should look like this:

```bash
# EDC Provider Configuration
PROVIDER_DOMAIN=http://eur.testbed
PROVIDER_HTTP_PORT=18190
PROVIDER_HTTP_MANAGEMENT_PORT=18191
PROVIDER_HTTP_PROTOCOL_PORT=18192
PROVIDER_HTTP_CONTROL_PORT=18193

# MinIO Configuration
MINIO_ENDPOINT=http://minio:9000
MINIO_BUCKET_NAME=your-bucket-name
MINIO_ACCESS_KEY=your-actual-access-key
MINIO_SECRET_KEY=your-actual-secret-key
MINIO_PREFIX=

# Asset Configuration
ASSET_NAME=your-asset-name
```

## Usage

### Create Asset

```bash
python create_asset.py
```

This script will:
1. Load configuration from `.env`
2. Create an asset in the EDC connector
3. Define a policy
4. Create contract definitions

### Delete Asset

```bash
python delete_asset.py
```

## Files

- `.env.example` - Template for environment variables (commit this)
- `.env` - Actual environment variables with secrets (DO NOT commit)
- `.gitignore` - Ensures `.env` is not committed
- `create_asset.py` - Script to create assets
- `delete_asset.py` - Script to delete assets

## Security Notes

- Never commit the `.env` file to version control
- Never share your access keys or secret keys
- Use different credentials for different environments (dev, staging, production)
- Rotate credentials regularly

## Troubleshooting

### "python-dotenv not installed"

Install the required package:
```bash
pip install python-dotenv
```

### "MinIO credentials not found"

Make sure:
1. You have created a `.env` file (copy from `.env.example`)
2. You have set `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` in the `.env` file
3. The `.env` file is in the same directory as the script

### Environment variables not loading

If you're using system environment variables instead of a `.env` file, make sure they are set:

```bash
# Linux/Mac
export MINIO_ACCESS_KEY=your-key
export MINIO_SECRET_KEY=your-secret

# Windows (PowerShell)
$env:MINIO_ACCESS_KEY="your-key"
$env:MINIO_SECRET_KEY="your-secret"
```