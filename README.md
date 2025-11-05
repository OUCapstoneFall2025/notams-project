# Smart NOTAM Prioritization System

**CS4273 - Fall 2025**  
**Group E**

## Description

This project addresses a critical aviation problem: pilots must sift through hundreds of Notices to Air Missions (NOTAMs) before every flight, often burying urgent safety information under irrelevant details. Our system will:

- Fetch NOTAMs for a given departure and destination airport in the continental U.S. via the FAA API.
- Prioritize the most safety-critical NOTAMs (e.g., runway closures, airspace restrictions).
- Display the results in a command-line interface (CLI) within 60 seconds, ensuring timely and clear flight briefings.

By surfacing the most relevant NOTAMs, this tool reduces information overload and improves safety for pilots.

---

## Identified Technologies & Tools

- **Java (21)** – Core programming language, chosen for performance, reliability, and object-oriented structure.
- **FAA API Portal** – Provides real-time NOTAM data through REST endpoints.
- **JUnit5** – Unit testing framework to ensure correctness of the NOTAM prioritization logic and promote test-driven development.
- **Jira** – Agile project management tool for sprint planning, daily updates, and retrospectives.
- **Git/GitHub** – Version control and collaboration platform for team development.

---

## Group Goals & Progress Plan

### Short-Term Goals (Sprint 1 & 2)

- Set up development environment and connect to the FAA API.
- Implement a Minimum Viable Product (MVP) that retrieves and displays NOTAMs via CLI.
- Build and test the NOTAM Prioritization Engine.
- Conduct sprint reviews with mentor/client to refine approach.

### Long-Term Goals (Sprint 3 & 4)

- Improve ranking heuristics for NOTAM usefulness.
- Expand automated testing coverage for reliability.
- Gather pilot/SME feedback to refine system output.
- Improve speed of returned results

---

## Running the Application

### Run with mock data (no API credentials needed):

```bash
./gradlew runWithMockData
```

This runs `ConnectToAPI` with mock data loaded from `src/main/resources/mock-faa-response.json`. No API credentials required - perfect for testing and development.

### Run with live API (requires credentials):

```bash
export FAA_CLIENT_ID="your_client_id"
export FAA_CLIENT_SECRET="your_client_secret"
./gradlew run --main-class ou.capstone.notams.ConnectToAPI
```

Or set the system property to use mock data:

```bash
./gradlew run --main-class ou.capstone.notams.ConnectToAPI -DConnectToApi.UseMockData=true
```

### Run the main App (currently just parses airport codes):

```bash
./gradlew run --args="--dep JFK --dest LAX"
```

**Note:** The main `App` class currently parses airport codes but doesn't fetch NOTAMs yet.

---

## Testing

### Run all unit tests (uses Mockito mocks, no API credentials needed):

```bash
./gradlew test
```

This runs all unit tests with mocked API responses. Tests use Mockito to mock `ConnectToAPI` calls, so no live API credentials are required.

### Run integration tests (requires live API access):

```bash
./gradlew integrationTest
```

**Note:** Integration tests require valid FAA API credentials set as environment variables:

- `FAA_CLIENT_ID`
- `FAA_CLIENT_SECRET`

Integration tests are disabled by default and only run when explicitly invoked with the `integrationTest` task.

### Run a single test:

```bash
./gradlew test --tests 'ou.capstone.notams.AppTest'
```

### Build (includes running unit tests):

```bash
./gradlew build
```

This will run all unit tests (with mocks) but will **not** run integration tests. Integration tests must be run separately with `./gradlew integrationTest`.

### Generate a report:

build/reports/tests/test/index.html

---

## Team Members

- Arianna Tobias
- Blake Wood
- Caleb Neher
- Jay Patel
- Raja Ahmed

**Mentor:** Brian Schettler
