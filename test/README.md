# Unit Tests

This directory contains JUnit 5 unit tests for the game components.

## Test Coverage

- **GameDataTest**: Tests for score, money, level progression, and upgrade management
- **BossTest**: Tests for boss health system, phases, mega boss patterns, and rewards
- **ComboSystemTest**: Tests for combo tracking, multipliers, timeouts, and max combo
- **AchievementTest**: Tests for achievement progress tracking and unlocking
- **PassiveUpgradeTest**: Tests for upgrade leveling, multipliers, and cost scaling
- **PlayerTest**: Tests for player movement, boundaries, velocity, and speed upgrades

## Running Tests

### Prerequisites

1. Download JUnit 5 dependencies:
   ```powershell
   # Create lib directory if it doesn't exist
   New-Item -ItemType Directory -Force -Path "../lib"
   
   # Download JUnit Platform Console Standalone (includes all dependencies)
   Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/junit-platform-console-standalone-1.10.1.jar" -OutFile "../lib/junit-platform-console-standalone-1.10.1.jar"
   ```

### Compile Tests

```powershell
# Compile the main source files first
javac -d ../bin -cp "../lib/*" ../src/*.java

# Compile test files
javac -d ../bin -cp "../bin;../lib/*" test/*.java
```

### Run All Tests

```powershell
# Run all tests using JUnit Console
java -jar ../lib/junit-platform-console-standalone-1.10.1.jar --class-path ../bin --scan-class-path
```

### Run Individual Test

```powershell
# Run a specific test class
java -jar ../lib/junit-platform-console-standalone-1.10.1.jar --class-path ../bin --select-class GameDataTest
```

## Test Results

Tests will output:
- ✓ Green checkmarks for passing tests
- ✗ Red X's for failing tests  
- Detailed failure messages including expected vs actual values
- Summary of total tests run, passed, and failed

## Adding New Tests

1. Create a new `*Test.java` file in this directory
2. Import JUnit 5 annotations: `import org.junit.jupiter.api.Test;`
3. Use `@Test` annotation for test methods
4. Use `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull` for assertions
5. Use `@BeforeEach` for setup code that runs before each test

Example:
```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MyTest {
    @BeforeEach
    public void setUp() {
        // Setup code
    }
    
    @Test
    public void testSomething() {
        assertEquals(expected, actual);
    }
}
```
