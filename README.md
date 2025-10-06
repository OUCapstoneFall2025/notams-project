# Smart NOTAM Prioritization System

**CS4273 - Fall 2025**  
**Group E**

## Description

This project addresses a critical aviation problem: pilots must sift through hundreds of Notices to Air Missions (NOTAMs) before every flight, often burying urgent safety information under irrelevant details. Our system will:

* Fetch NOTAMs for a given departure and destination airport in the continental U.S. via the FAA API.
* Prioritize the most safety-critical NOTAMs (e.g., runway closures, airspace restrictions).
* Display the results in a command-line interface (CLI) within 60 seconds, ensuring timely and clear flight briefings.

By surfacing the most relevant NOTAMs, this tool reduces information overload and improves safety for pilots.

---

## Identified Technologies & Tools

* **Java (21)** – Core programming language, chosen for performance, reliability, and object-oriented structure.
* **FAA API Portal** – Provides real-time NOTAM data through REST endpoints.
* **JUnit5** – Unit testing framework to ensure correctness of the NOTAM prioritization logic and promote test-driven development.
* **Jira** – Agile project management tool for sprint planning, daily updates, and retrospectives.
* **Git/GitHub** – Version control and collaboration platform for team development.

---

## Group Goals & Progress Plan

### Short-Term Goals (Sprint 1 & 2)

* Set up development environment and connect to the FAA API.
* Implement a Minimum Viable Product (MVP) that retrieves and displays NOTAMs via CLI.
* Build and test the NOTAM Prioritization Engine.
* Conduct sprint reviews with mentor/client to refine approach.

### Long-Term Goals (Sprint 3 & 4)

* Improve ranking heuristics for NOTAM usefulness.
* Expand automated testing coverage for reliability.
* Gather pilot/SME feedback to refine system output.
* Improve speed of returned results


---

## Testing 

### Run all tests:
./gradlew test

### Run a single test:
./gradlew test --tests 'ou.capstone.notams.AppTest'

### Generate a report:
build/reports/tests/test/index.html

---

## Team Members

* Arianna Tobias
* Blake Wood
* Caleb Neher
* Jay Patel
* Raja Ahmed

**Mentor:** Brian Schettler
