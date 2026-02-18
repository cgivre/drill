# Corporate SharePoint/OneDrive Connector - Advanced Configuration Guide

This guide covers how to use the SharePoint/OneDrive connector with corporate SharePoint sites (e.g., `https://mycorp.sharepoint.com`).

## Understanding SharePoint Architecture

### URL Structure
```
Personal OneDrive:      https://mycorp-my.sharepoint.com/personal/user_mycorp_com/
Team Sites:             https://mycorp.sharepoint.com/sites/TeamName
Communication Sites:    https://mycorp.sharepoint.com/sites/CompanyNews
Sub-sites:              https://mycorp.sharepoint.com/sites/TeamName/projects/ProjectA
```

### Graph API Endpoints
```
OneDrive for Business:     /me/drive
Team Site Drive:           /sites/{siteId}/drive
Team Site Lists:           /sites/{siteId}/lists
Communications Site:       /sites/{siteId}/drive (same as Team Site)
```

## Path Mapping for Corporate Sites

The connector maps Drill paths to Microsoft 365 resources:

| Drill Path | Use Case | Graph Endpoint |
|-----------|----------|----------------|
| `/me/...` | User's OneDrive for Business | `/me/drive/root:/path:` |
| `/sites/Sales/drive/...` | Team Site by name | `/sites/{siteId}/drive/...` |
| `/sites/{siteId}/drive/...` | Team Site by ID | Direct access |
| `/sites/Sales/lists/Opportunities` | SharePoint List | `/sites/{siteId}/lists/{listId}/items` |

## Configuration for Corporate SharePoint

### Basic Configuration with Team Site

```json
{
  "type": "file",
  "connection": "sharepoint:///",
  "config": {
    "sharepointAccessToken": "eyJ0eXAiOiJKV1QiLCJhbGc..."
  },
  "workspaces": {
    "root": {
      "location": "/",
      "writable": false
    },
    "sales_team": {
      "location": "/sites/Sales/drive",
      "writable": false
    },
    "marketing_team": {
      "location": "/sites/Marketing/drive",
      "writable": false
    }
  },
  "formats": {
    "csv": {
      "type": "text",
      "extensions": ["csv"],
      "delimiter": ",",
      "quote": "\""
    },
    "excel": {
      "type": "excel",
      "extensions": ["xlsx", "xls"]
    },
    "json": {
      "type": "json",
      "extensions": ["json"]
    }
  },
  "enabled": true
}
```

### OAuth 2.0 Configuration for Corporate Deployments

```json
{
  "type": "file",
  "connection": "sharepoint:///",
  "workspaces": {
    "root": {
      "location": "/",
      "writable": false
    },
    "sales": {
      "location": "/sites/Sales/drive",
      "writable": false
    },
    "lists": {
      "location": "/sites/Sales/lists",
      "writable": false
    }
  },
  "oAuthConfig": {
    "callbackURL": "http://drill-server:8047/credentials/sharepoint/update_oauth2_authtoken",
    "authorizationURL": "https://login.microsoftonline.com/mycorp.onmicrosoft.com/oauth2/v2.0/authorize",
    "tokenURI": "https://login.microsoftonline.com/mycorp.onmicrosoft.com/oauth2/v2.0/token",
    "authorizationParams": {
      "response_type": "code",
      "scope": "Files.Read Sites.Read.All offline_access"
    }
  },
  "credentialsProvider": {
    "credentialsProviderType": "PlainCredentialsProvider",
    "credentials": {
      "clientID": "12345678-1234-1234-1234-123456789012",
      "clientSecret": "abc123def456ghi789jkl012mno345pqr678",
      "tenantID": "mycorp.onmicrosoft.com"
    }
  },
  "enabled": true
}
```

## SQL Usage Examples

### Accessing OneDrive for Business

```sql
-- List personal drive contents
SHOW FILES IN sharepoint.root;

-- Query a CSV from OneDrive
SELECT * FROM sharepoint.`/me/Documents/sales.csv` LIMIT 100;

-- Query Excel file (requires Excel format plugin)
SELECT * FROM sharepoint.`/me/Reports/annual_summary.xlsx` WHERE year = 2023;
```

### Accessing Team Site Documents

```sql
-- List Team Site drive contents
SHOW FILES IN sharepoint.sales_team;

-- Query Team Site document
SELECT * FROM sharepoint.`/sites/Sales/drive/Q4_Results/forecast.csv`;

-- Query from workspace
SELECT * FROM sharepoint_plugin.sales_team.`forecast.csv`;
```

### Querying SharePoint Lists

```sql
-- Query a SharePoint List as JSON
SELECT * FROM sharepoint.`/sites/Sales/lists/Opportunities`
WHERE status = 'Active'
AND amount > 100000;

-- Flatten nested list items
SELECT
  id,
  fields['Title'] as title,
  fields['Amount'] as amount,
  fields['Owner'] as owner
FROM sharepoint.`/sites/Sales/lists/Opportunities`
WHERE fields['Status'] = 'Proposal';

-- Join list data with drive files
SELECT
  l.fields['Title'] as opportunity_name,
  l.fields['Amount'] as amount,
  l.fields['Owner'] as owner,
  CAST(d.modificationTime as DATE) as last_updated
FROM sharepoint.`/sites/Sales/lists/Opportunities` l
LEFT JOIN sharepoint.`/sites/Sales/drive/Supporting_Docs` d
  ON l.fields['Title'] = d.name;
```

### Querying Multiple Team Sites

```sql
-- Compare data across team sites
SELECT
  'Sales' as team,
  COUNT(*) as record_count,
  SUM(CAST(amount as DECIMAL)) as total
FROM sharepoint.`/sites/Sales/drive/reports/ytd.csv`
UNION ALL
SELECT
  'Marketing' as team,
  COUNT(*) as record_count,
  SUM(CAST(budget as DECIMAL)) as total
FROM sharepoint.`/sites/Marketing/drive/reports/ytd.csv`;
```

## Site Discovery

### Finding Site IDs

If you don't know the exact site name or ID, you can:

**Option 1: Use Microsoft Graph Explorer**
1. Go to https://developer.microsoft.com/en-us/graph/graph-explorer
2. Run: `GET /sites?search=Sales`
3. Copy the `id` value from results

**Option 2: Use Azure CLI**
```bash
az login
az rest --method get --uri "https://graph.microsoft.com/v1.0/sites?search=Sales" \
  --headers "Accept=application/json"
```

**Option 3: Browse via web**
1. Go to your SharePoint site: `https://mycorp.sharepoint.com`
2. Click the site name to see the URL pattern
3. Extract the site display name for Drill

### Example Site Resolution

For a site at `https://mycorp.sharepoint.com/sites/Sales-Q4`

```sql
-- These would all work:
SELECT * FROM sharepoint.`/sites/Sales-Q4/drive/reports/forecast.xlsx`;

-- Or if you have the Graph site ID:
SELECT * FROM sharepoint.`/sites/mycorp.sharepoint.com,12345678-1234-1234-1234-123456789012/drive/reports/forecast.xlsx`;
```

## Performance Considerations for Corporate Deployments

### Network and Latency
- **File Download**: Files are buffered in memory; large files (>500MB) may cause issues
- **API Rate Limiting**: Microsoft Graph has rate limits (typically 2000 requests/minute)
- **Network Latency**: Consider adding caching for frequently accessed sites

### Optimization Strategies

**1. Use Workspaces for Frequently Accessed Sites**
```json
{
  "workspaces": {
    "sales": { "location": "/sites/Sales/drive", "writable": false },
    "marketing": { "location": "/sites/Marketing/drive", "writable": false }
  }
}
```

**2. Use File Filters in SQL**
```sql
-- Good: Filters before reading
SELECT * FROM sharepoint.`/sites/Sales/drive/reports/data.csv`
WHERE region = 'US' AND year = 2023;

-- Less efficient: Full scan then filter
SELECT * FROM sharepoint.`/sites/Sales/drive/reports/data.csv`
WHERE 1=1;  -- Reads entire file
```

**3. Cache Frequently Accessed Data**
```sql
-- Create cached view
CREATE TABLE sales_opportunities_cache AS
SELECT * FROM sharepoint.`/sites/Sales/lists/Opportunities`
WHERE status IN ('Active', 'Proposal');
```

## Azure App Registration for Corporate Environment

### Prerequisites
- Admin access to Azure AD in your organization
- Microsoft 365 subscription with SharePoint

### Step-by-Step Setup

1. **Register Application in Azure AD**
   - Azure Portal → Azure Active Directory → App Registrations
   - New Registration:
     - Name: `Apache Drill SharePoint Connector`
     - Supported account types: `Accounts in this organizational directory only`
     - Redirect URI: `http://your-drill-server:8047/credentials/sharepoint/update_oauth2_authtoken`

2. **Configure API Permissions**
   - API permissions → Add a permission → Microsoft Graph
   - Delegated permissions:
     - `Files.Read` - Read files in SharePoint/OneDrive
     - `Sites.Read.All` - Read all SharePoint sites
     - `offline_access` - Use refresh tokens
   - Grant admin consent

3. **Create Client Secret**
   - Certificates & secrets → New client secret
   - Copy the Value (use as `clientSecret`)

4. **Collect Credentials**
   - Application (client) ID → `clientID`
   - Directory (tenant) ID → `tenantID` (format: `12345678-1234-...` or `mycorp.onmicrosoft.com`)
   - Client secret Value → `clientSecret`

5. **Test with Graph Explorer**
   - Go to Graph Explorer: https://developer.microsoft.com/graph/graph-explorer
   - Sign in with your corporate account
   - Run test queries:
     ```
     GET /me/drive/root
     GET /sites?search=Sales
     GET /sites/{siteId}/lists
     ```

## Troubleshooting Corporate SharePoint Issues

### Issue: "Site not found" error

**Cause**: Site name resolution failed
**Solutions**:
```sql
-- Try exact site display name
SELECT * FROM sharepoint.`/sites/Sales_Team/drive/file.csv`;

-- Try URL-encoded name
SELECT * FROM sharepoint.`/sites/Sales%20Team/drive/file.csv`;

-- Use site ID directly
SELECT * FROM sharepoint.`/sites/contoso.sharepoint.com,12345678-1234-1234-1234-123456789012/drive/file.csv`;
```

### Issue: "Permission denied" for a list

**Cause**: User doesn't have list read permissions
**Solutions**:
- Check SharePoint list permissions
- Verify service account has `Sites.Read.All` permission
- Check if list items have item-level permissions

### Issue: Files disappear after a few minutes

**Cause**: Token expired
**Solutions**:
- Use OAuth 2.0 mode instead of static token
- Static tokens don't refresh automatically
- Implement token refresh in scheduled jobs

### Issue: Slow performance on large lists

**Cause**: All list items loaded into memory
**Solutions**:
- Use WHERE clauses to limit data
- Implement pagination in future enhancements
- Use CSV export from SharePoint for large datasets

## Advanced: Custom Site Mapping

To simplify access to frequently used sites, map them to workspace locations:

```json
{
  "workspaces": {
    "analytics": {
      "location": "/sites/DataAnalytics/drive/Reports",
      "writable": false
    },
    "compliance": {
      "location": "/sites/Legal/drive/Compliance",
      "writable": false
    },
    "hr_lists": {
      "location": "/sites/HR/lists",
      "writable": false
    }
  }
}
```

Usage becomes cleaner:
```sql
-- Without workspace (long path)
SELECT * FROM sharepoint.`/sites/DataAnalytics/drive/Reports/monthly.csv`;

-- With workspace (clean path)
SELECT * FROM sharepoint_plugin.analytics.`monthly.csv`;
```

## Future Enhancements for Corporate Deployments

These features would make the connector more suitable for enterprise use:

- [ ] **Streaming large files** - Instead of in-memory buffering
- [ ] **Batch operations** - Multi-file queries in single request
- [ ] **Pagination** - Efficient listing of large folders/lists
- [ ] **Write support** - Upload, create, update operations
- [ ] **Change tracking** - Incremental syncs using delta queries
- [ ] **Site/List discovery** - Browse available resources
- [ ] **Advanced caching** - Distributed cache for site metadata
- [ ] **Retention policies** - Handle archived content
- [ ] **Delegation** - Service account acting on behalf of users
- [ ] **Multi-geo support** - Access sites in different geographic regions

## Integration with Drill Metadata

Once data is queried via SharePoint, you can leverage Drill's full power:

```sql
-- Join SharePoint data with other sources
SELECT
  sp.product_id,
  sp.revenue,
  local.local_name,
  local.category
FROM sharepoint.`/sites/Sales/drive/products.csv` sp
INNER JOIN postgres.public.products local
  ON sp.product_id = local.id;

-- Create views for easy access
CREATE VIEW sales_revenue AS
SELECT * FROM sharepoint.`/sites/Sales/drive/reports/revenue.csv`
WHERE quarter = 'Q4';

-- Export results back to another system
CREATE TABLE hive.default.sharepoint_sales AS
SELECT * FROM sharepoint.`/sites/Sales/drive/data.csv`;
```

## JDBC Driver Considerations

### JDBC Driver Size Constraint

The Apache Drill JDBC driver (`drill-jdbc-all`) has a size limit of 62MB to minimize dependencies for JDBC users. Adding the SharePoint connector dependencies increases the total dependency footprint.

**If you're using the JDBC driver distribution:**

The SharePoint connector requires:
- `com.microsoft.graph:microsoft-graph` (2.5MB+)
- `com.microsoft.azure:msal4j` (1MB+)
- Transitive dependencies (azure-core, kiota, jackson, reactor-core, etc.)

**Total addition: ~30MB+**

This may exceed your deployment constraints.

**Solutions:**

1. **Use Drill Server Distribution (Recommended)**
   - Download the full Drill server from [Apache Drill downloads](https://drill.apache.org/download/)
   - No size constraints - includes all storage plugins
   - Best for enterprise deployments

2. **Build Custom JDBC with Larger Size Limit**
   ```bash
   # Edit exec/jdbc-all/pom.xml
   # Change: <jdbc-all-jar.maxsize>62000000</jdbc-all-jar.maxsize>
   # To: <jdbc-all-jar.maxsize>150000000</jdbc-all-jar.maxsize>

   mvn clean package -pl exec/jdbc-all
   ```

3. **Exclude SharePoint from JDBC JAR**
   - Build Drill server normally (no SharePoint needed in JDBC driver)
   - Use server with embedded drivers for SharePoint queries
   - Use JDBC driver separately for non-SharePoint scenarios

4. **Use Drill Embedded**
   - Embed Drill directly in your application
   - No JDBC JAR size limits
   - Direct access to all plugins including SharePoint

## Support and Resources

- [Microsoft Graph API Docs](https://docs.microsoft.com/en-us/graph/overview)
- [SharePoint REST API](https://docs.microsoft.com/en-us/sharepoint/dev/sp-add-ins/get-to-know-the-sharepoint-rest-service)
- [Azure AD Documentation](https://docs.microsoft.com/en-us/azure/active-directory/)
- [Apache Drill Documentation](https://drill.apache.org/)
