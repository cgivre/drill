# SharePoint/OneDrive Connector for Apache Drill

This document describes the SharePoint/OneDrive connector implementation for Apache Drill, which enables querying files stored in Microsoft 365 environments.

## Overview

The SharePoint/OneDrive connector allows Apache Drill to:
- Query files stored in OneDrive personal drives
- Access files in SharePoint document libraries
- Treat SharePoint Lists as queryable JSON data sources
- Use the familiar Drill SQL interface to explore Microsoft 365 content

The connector follows the same architectural pattern as the existing Dropbox and Box file system connectors, making it a natural fit within Drill's storage plugin ecosystem.

## Architecture

### Design Principles

1. **Pattern Consistency**: Extends `OAuthEnabledFileSystem` like Dropbox/Box
2. **Read-Only**: Initial implementation is read-only; write operations throw `IOException`
3. **In-Memory Buffering**: Files are downloaded completely before reading (suitable for BI query sizes)
4. **Flexible Authentication**:
   - Static token mode (for development/testing)
   - OAuth 2.0 dynamic mode (for production)
5. **Native Graph Integration**: Uses Microsoft Graph API v1.0 for all operations

### Components

**SharePointFileSystem.java** (~290 lines)
- Main implementation extending `OAuthEnabledFileSystem`
- Implements Hadoop `FileSystem` contract
- Uses `OkHttpClient` for REST calls to Microsoft Graph API
- Manages token lifecycle through `PersistentTokenTable`

**Maven Dependencies**
- `com.microsoft.graph:microsoft-graph:6.11.0` - Graph SDK
- `com.microsoft.azure:msal4j:1.17.0` - Azure authentication

### Path Convention

| SQL Path | Description | Graph API Endpoint |
|----------|-------------|-------------------|
| `sharepoint:///` | Root (OneDrive) | `/me/drive/root` |
| `sharepoint:///me/Documents/file.csv` | OneDrive file | `/me/drive/root:/path:/content` |
| `sharepoint:///sites/MySite/drive/...` | SharePoint site drive | `/sites/{siteId}/drive/...` |
| `sharepoint:///sites/MySite/lists/MyList` | SharePoint List as JSON | `/sites/{siteId}/lists/{listId}/items` |

## Configuration

### Minimal Configuration (Static Token)

For development/testing, use a static access token:

```json
{
  "type": "file",
  "connection": "sharepoint:///",
  "config": {
    "sharepointAccessToken": "YOUR_ACCESS_TOKEN_HERE"
  },
  "workspaces": {
    "root": {
      "location": "/",
      "writable": false,
      "defaultInputFormat": null
    }
  },
  "formats": {
    "csv": {
      "type": "text",
      "extensions": ["csv"],
      "delimiter": ",",
      "quote": "\""
    },
    "json": {
      "type": "json",
      "extensions": ["json"]
    }
  },
  "enabled": true
}
```

### Full Configuration (OAuth 2.0)

For production deployments with dynamic token management:

```json
{
  "type": "file",
  "connection": "sharepoint:///",
  "workspaces": {
    "root": {
      "location": "/",
      "writable": false
    }
  },
  "oAuthConfig": {
    "callbackURL": "http://localhost:8047/credentials/sharepoint/update_oauth2_authtoken",
    "authorizationURL": "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize",
    "authorizationParams": {
      "response_type": "code",
      "scope": "Files.Read Sites.Read.All offline_access"
    }
  },
  "credentialsProvider": {
    "credentialsProviderType": "PlainCredentialsProvider",
    "credentials": {
      "clientID": "YOUR_CLIENT_ID",
      "clientSecret": "YOUR_CLIENT_SECRET",
      "tenantID": "YOUR_TENANT_ID"
    }
  },
  "enabled": true
}
```

## Usage

### Basic Queries

After configuring the `sharepoint_test` storage plugin:

```sql
-- List files in OneDrive root
SHOW FILES IN sharepoint.root;

-- Query a CSV file from OneDrive
SELECT * FROM sharepoint.`/me/Documents/sales.csv` LIMIT 10;

-- Query a JSON file
SELECT * FROM sharepoint.`/me/data.json` WHERE region = 'US';

-- Query a SharePoint List (returns JSON array)
SELECT * FROM sharepoint.`/sites/Sales/lists/Opportunities`
WHERE status = 'Open';
```

### Example Data Access Patterns

**CSV Analysis**
```sql
SELECT
  region,
  COUNT(*) as record_count,
  SUM(amount) as total_amount
FROM sharepoint.`/me/sales_data.csv`
GROUP BY region;
```

**JSON Nested Queries**
```sql
SELECT
  metadata.created_date,
  data.customer_name,
  data.order_value
FROM sharepoint.`/me/orders.json`
WHERE data.status = 'completed';
```

**SharePoint List Integration**
```sql
SELECT
  l.title,
  l.assigned_to,
  l.due_date
FROM sharepoint.`/sites/ProjectA/lists/Tasks` l
WHERE l.status != 'Completed'
ORDER BY l.due_date;
```

## Setup Instructions

### 1. Azure App Registration

#### Create Application Registration

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** → **App Registrations**
3. Click **+ New registration**
4. Fill in:
   - **Name**: `Drill SharePoint Connector`
   - **Supported account types**: Choose based on your organization (typically "Accounts in this organizational directory only")
   - **Redirect URI**: `http://localhost:8047/credentials/sharepoint/update_oauth2_authtoken` (for development)
5. Click **Register**

#### Configure API Permissions

1. Go to **API permissions**
2. Click **+ Add a permission**
3. Select **Microsoft Graph**
4. Select **Delegated permissions**
5. Add these permissions:
   - `Files.Read` - Read OneDrive and SharePoint files
   - `Sites.Read.All` - Read all SharePoint sites
   - `offline_access` - Maintain access to data (for refresh tokens)
6. Click **Grant admin consent**

#### Get Credentials

1. Go to **Certificates & secrets**
2. Click **+ New client secret**
3. Description: `Drill Access`
4. Expiration: Choose appropriate duration
5. Click **Add**
6. Copy the **Value** (this is your `clientSecret`)

7. From **Overview** page, copy:
   - **Application (client) ID** → use as `clientID`
   - **Directory (tenant) ID** → use as `tenantID`

### 2. Configure Drill Plugin

Create `sharepoint.json` in Drill's storage plugins directory:

```json
{
  "type": "file",
  "connection": "sharepoint:///",
  "config": {
    "sharepointAccessToken": "YOUR_TOKEN_HERE"
  },
  "workspaces": {
    "root": {
      "location": "/",
      "writable": false
    }
  },
  "enabled": true
}
```

### 3. Restart Drill

Reload storage plugins:
```sql
ALTER SYSTEM SET `exec.storage_plugins.update_enabled` = true;
```

Or restart the Drillbit service.

### 4. Verify Connection

```sql
SHOW FILES IN sharepoint.root;
```

## Testing

### Unit Tests (No Azure Access Required)

Run mock-based tests that verify core functionality:

```bash
mvn test -pl exec/java-exec \
  -Dtest=SharePointFileSystemMockTest \
  -DfailIfNoTests=false
```

These tests verify:
- URI scheme correctness
- Working directory management
- Read-only enforcement
- Path parsing
- Configuration loading

### Integration Tests (Requires Azure Access)

Integration tests require a valid access token and connection to Microsoft 365:

1. **Get an access token** using Microsoft Graph Explorer or Azure CLI:
   ```bash
   # Using Azure CLI
   az account get-access-token --resource https://graph.microsoft.com
   ```

2. **Set the token in the test class**:
   ```java
   private static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGc...";
   ```

3. **Remove the `@Ignore` annotation** from test methods

4. **Run integration tests**:
   ```bash
   mvn test -pl exec/java-exec \
     -Dtest=SharePointFileSystemTest \
     -DfailIfNoTests=false
   ```

### Test Coverage

**Unit Tests** (`SharePointFileSystemMockTest.java`):
- ✓ URI scheme validation
- ✓ Working directory operations
- ✓ Root directory access
- ✓ Read-only enforcement
- ✓ Configuration handling
- ✓ Path parsing

**Integration Tests** (`SharePointFileSystemTest.java`):
- ✓ List OneDrive files
- ✓ Query JSON files
- ✓ Query CSV files
- ✓ SharePoint List queries (when implemented)

## Limitations

### Current Implementation

1. **Read-Only Access**: No support for uploading, creating, or modifying files
2. **In-Memory Buffering**: Large files are loaded completely into memory
3. **SharePoint Lists**: Basic structure only; full list item expansion pending
4. **Pagination**: Not yet implemented for large directory listings
5. **Caching**: Limited caching of site name→ID mappings

### Future Enhancements

- [ ] Write operations (upload, create files)
- [ ] Streaming large file access (instead of in-memory buffering)
- [ ] Full SharePoint List support with metadata expansion
- [ ] Site/drive discovery and browsing
- [ ] Batch operations for improved performance
- [ ] Incremental refresh tokens
- [ ] Comprehensive caching strategy

## Troubleshooting

### Authentication Errors

**Error**: `No access token found in configuration or credentials provider`
- Verify `sharepointAccessToken` is set in config
- Check OAuth credentials (clientID, clientSecret, tenantID)
- Ensure access token hasn't expired

**Error**: `Graph API request failed: 401 Unauthorized`
- Token may have expired; get a new one
- Verify API permissions granted in Azure Portal
- Check tenant ID is correct

### File Access Errors

**Error**: `Error accessing file /path: 404 Not Found`
- Verify the file path exists in OneDrive
- Check path syntax matches Graph API requirements
- Try listing parent directory first

**Error**: `Cannot open directory as file`
- Attempting to open a folder as a file
- Verify path points to actual file, not directory
- Use `SHOW FILES IN` to list directory contents

### Performance Issues

**Issue**: Queries are slow
- Check file size; large files are buffered in memory
- Verify network connectivity to Microsoft Graph API
- Check Azure subscription limits haven't been exceeded
- Consider adding filters to reduce data scanned

## Implementation Notes

### Token Management

Tokens are managed through Drill's `PersistentTokenTable`:
- Access tokens are refreshed as needed
- Refresh tokens are persisted automatically
- Token expiration is handled transparently

### Graph API Integration

All file operations go through Microsoft Graph API v1.0:
- `/me/drive` - User's OneDrive
- `/sites/{siteId}/drive` - SharePoint site drive
- `/sites/{siteId}/lists/{listId}/items` - SharePoint Lists
- Individual item access via path lookups

### Error Handling

- File not found → `IOException`
- Permission denied → `IOException` with error details
- Network errors → `IOException` with cause
- Configuration errors → `UserException` at initialization

## Contributing

To extend the SharePoint connector:

1. **Add file write support**:
   - Implement `create()`, `append()`, `delete()` in `SharePointFileSystem`
   - Add upload logic to handle file content

2. **Improve SharePoint List support**:
   - Enhance `fetchSharePointListAsJson()` with full metadata
   - Add list field type mapping

3. **Optimize performance**:
   - Add paginated listing for large directories
   - Implement caching strategy
   - Add streaming file download

4. **Enhance authentication**:
   - Support service principal authentication
   - Add multi-tenant support
   - Implement token refresh optimization

## References

- [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/overview)
- [OneDrive API Reference](https://docs.microsoft.com/en-us/onedrive/developer/rest-api/)
- [SharePoint REST API](https://docs.microsoft.com/en-us/sharepoint/dev/sp-add-ins/get-to-know-the-sharepoint-rest-service)
- [MSAL4J Documentation](https://github.com/AzureAD/microsoft-authentication-library-for-java)
- [Apache Drill Storage Plugins](https://drill.apache.org/docs/storage-plugin-registration/)

## Support

For issues related to:
- **Drill functionality**: Check [Drill JIRA](https://issues.apache.org/jira/browse/DRILL)
- **Microsoft Graph API**: See [Microsoft Graph troubleshooting](https://docs.microsoft.com/en-us/graph/api/overview)
- **Azure authentication**: Consult [Azure AD documentation](https://docs.microsoft.com/en-us/azure/active-directory/)

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) file for details.
