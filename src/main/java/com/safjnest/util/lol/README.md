# League of Legends Commands - Curl Conversion

This directory contains the League of Legends command handlers that have been converted from using Java's `HttpURLConnection` to using external `curl` commands via `ProcessBuilder`.

## Files Converted

### MobalyticsHandler.java
- **Method**: `getChampioStats(String champName, String lane)`
- **API**: Mobalytics GraphQL endpoint
- **Conversion**: Replaced HttpURLConnection with curl POST request
- **Changes**:
  - Uses ProcessBuilder to execute curl command
  - Creates temporary files for JSON payload
  - Handles curl exit codes and error streams
  - Maintains the same JSON parsing logic for response data

### LeagueHandler.java (Partial)
- **Method**: `getBraveryBuildJSON(int level, String[] roles, String[] champions)`
- **Method**: `getBraveryBuildJSON()` (overloaded with defaults)
- **API**: Ultimate Bravery API endpoint
- **Conversion**: Replaced HttpURLConnection with curl POST request
- **Changes**:
  - Uses ProcessBuilder to execute curl command
  - Creates temporary files for JSON payload
  - Handles curl exit codes and error streams

## Key Changes Made

1. **HTTP Client Replacement**: 
   - Old: `HttpURLConnection` with manual header setting and stream handling
   - New: `curl` command with appropriate flags via `ProcessBuilder`

2. **Request Body Handling**:
   - Old: Direct write to `OutputStream`
   - New: Write JSON to temporary file, use `--data-binary @file` with curl

3. **Response Handling**:
   - Old: Read from `InputStream`
   - New: Read from `Process.getInputStream()`

4. **Error Handling**:
   - Old: HTTP response codes via `HttpURLConnection.getResponseCode()`
   - New: Process exit codes and error stream reading

## Benefits of Curl Conversion

1. **External Tool Leverage**: Uses the robust, well-tested curl tool
2. **Network Debugging**: Easier to debug network issues with curl's verbose options
3. **Platform Independence**: Curl is available on most systems
4. **Configuration Flexibility**: Easy to add curl-specific options (timeouts, retries, etc.)

## Testing

Run `CurlTestExample.java` to test the converted functionality. Note that network access may be limited in some environments.

## Dependencies

- Requires `curl` to be installed and available in system PATH
- Requires Java with ProcessBuilder support
- Temporary file creation capabilities