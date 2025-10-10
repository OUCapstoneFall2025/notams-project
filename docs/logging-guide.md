# Logging System Documentation

## Overview

The NOTAMs project uses SLF4J with Logback for comprehensive logging and debugging. The system captures application events, API interactions, and error handling to facilitate debugging and monitoring.

## Logging Configuration

### Log Files

- **`logs/notams-app.log`** - Main application logs with daily rotation
- **`logs/api-interactions.log`** - Dedicated FAA API interaction logs

### Log Levels

- **INFO** - Application lifecycle, important events
- **DEBUG** - Detailed debugging information (argument parsing, airport normalization)
- **WARN** - Warning messages (API non-success status codes)
- **ERROR** - Error conditions (missing credentials, console unavailable)

## How to Run with Logging

### Basic Usage

```bash
# Run with default INFO level logging
./gradlew run --args="--dep JFK --dest LAX"

# Run with debug logging enabled
./gradlew run --args="--dep OKC --dest DFW" -Droot.level=DEBUG
```

### Testing Different Log Levels

```bash
# INFO level (default) - shows main application flow
./gradlew run --args="--dep JFK --dest LAX"

# DEBUG level - shows detailed argument parsing and airport normalization
./gradlew run --args="--dep OKC --dest DFW" -Droot.level=DEBUG

# WARN level and above
./gradlew run --args="--dep JFK --dest LAX" -Droot.level=WARN
```

### Viewing Log Files

```bash
# View main application logs
cat logs/notams-app.log

# View API interaction logs
cat logs/api-interactions.log

# Follow logs in real-time
tail -f logs/notams-app.log
```

## Log Output Examples

### Console Output (INFO level)

```
19:06:57.399 [main] INFO  ou.capstone.notams.App - NOTAMs CLI application starting
NOTAMs CLI ready.
Raw args: [--dep, JFK, --dest, LAX]
19:06:57.400 [main] INFO  ou.capstone.notams.App - Flight route - Departure: KJFK, Destination: KLAX
Departure: KJFK
Destination: KLAX
19:06:57.401 [main] INFO  ou.capstone.notams.App - NOTAMs CLI application completed successfully
```

### File Output (logs/notams-app.log)

```
2025-09-29 19:06:57.399 [main] INFO  ou.capstone.notams.App - NOTAMs CLI application starting
2025-09-29 19:06:57.400 [main] INFO  ou.capstone.notams.App - Flight route - Departure: KJFK, Destination: KLAX
2025-09-29 19:06:57.401 [main] INFO  ou.capstone.notams.App - NOTAMs CLI application completed successfully
```

## Testing the Logging System

### 1. Test Basic Logging

```bash
./gradlew run --args="--dep JFK --dest LAX"
```

Check that `logs/notams-app.log` is created and contains the application flow.

### 2. Test Debug Logging

```bash
./gradlew run --args="--dep OKC --dest DFW" -Droot.level=DEBUG
```

Verify detailed argument parsing and airport code normalization logs appear.

### 3. Test Airport Code Normalization

```bash
./gradlew run --args="--dep OKC --dest DFW"  # 3-letter codes
./gradlew run --args="--dep KJFK --dest KLAX"  # ICAO codes
```

Both should normalize to ICAO format (KOKC, KDFW, KJFK, KLAX).

### 4. Test Error Handling

```bash
./gradlew run --args="--dep JFK"  # Missing destination
```

Check that appropriate error handling and logging occurs.

## Log Rotation

- Logs rotate daily at midnight
- Maximum file size: 10MB before rotation
- Retention: 30 days for main logs, 500MB total for API logs
- Compression: Disabled for easier debugging

## Troubleshooting

### Log Files Not Created

- Ensure the `logs/` directory exists
- Check file permissions
- Verify Gradle build completed successfully

### Import Errors in IDE

- Refresh Gradle project in your IDE
- Restart IDE if refresh doesn't work
- Dependencies are correctly configured in `build.gradle`

### Logging Not Appearing

- Check log level settings
- Verify `logback.xml` is in `src/main/resources/`
- Ensure application is using the correct configuration
