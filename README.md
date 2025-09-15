Here's our readme for our project.

# NOTAMs Project (OU CS4273 Capstone – Group E)

This is the Java 21 + Gradle CLI application for processing and prioritizing FAA NOTAMs.

---

## Prerequisites
- **Java 21** (Temurin/OpenJDK recommended)
  ```bash
  java -version
  # should show 21.x

## Project layout
- app/                   # main application module
  └─ src/
     ├─ main/java/edu/ou/capstone/notams/App.java
     └─ test/java/edu/ou/capstone/notams/AppTest.java

## Run
./gradlew run --args="--dep KJFK --dest KATL"


## Build & test
./gradlew build
./gradlew test

## Some commands that may be used later
./gradlew spotlessApply
./gradlew check
