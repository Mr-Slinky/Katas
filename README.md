# Katas

Katas is a Java project that students download and extend. A student writes their own methods in a
copy of the code, and Katas runs those methods and shows each step of the run as a visual output.

## Trace Tables

The trace table is the first visual output planned for Katas. A trace table has one column for each
variable and each condition, and one row for each step of a method. Each cell shows the value that
variable or condition had at that step, meaning a student reads down the table to follow how the
method changed its variables and which way each condition went.

A student tracing the loop below by hand would write one row each time a variable changes or the
condition is checked. The numbers down the left of the code are line numbers:

```java
1  int sum = 0;
2  for (int i = 1; i <= 3; i++) {
3      sum += i;
4  }
```

The far-left column of the trace table gives the line on which each step happens. A variable's cell
is filled in only on the row where its value changes, meaning a blank cell keeps the value written
above it:

| Line | Statement  | `sum` | `i` | `i <= 3` |
|------|------------|-------|-----|----------|
| 1    | `sum = 0`  | 0     |     |          |
| 2    | `i = 1`    |       | 1   |          |
| 2    | `i <= 3`   |       |     | true     |
| 3    | `sum += i` | 1     |     |          |
| 2    | `i++`      |       | 2   |          |
| 2    | `i <= 3`   |       |     | true     |
| 3    | `sum += i` | 3     |     |          |
| 2    | `i++`      |       | 3   |          |
| 2    | `i <= 3`   |       |     | true     |
| 3    | `sum += i` | 6     |     |          |
| 2    | `i++`      |       | 4   |          |
| 2    | `i <= 3`   |       |     | false    |

The loop stops on the last row, where `i <= 3` evaluates to false.

## Requirements

Katas builds with Java 25 or later and Maven 3.9 or later. The Maven Enforcer plugin fails the build
on any older version of either. The tests use JUnit 5 (Jupiter), which Maven downloads during the
first build.

## Installing Java and Maven

A student installs Java and Maven once, with the package manager for their operating system.

### Windows

A student on Windows installs Eclipse Temurin JDK 25 and the Chocolatey package manager with winget.
Both commands run in a PowerShell window opened with **Run as administrator**:

```
winget install --id EclipseAdoptium.Temurin.25.JDK -e
winget install --id Chocolatey.Chocolatey -e
```

The student then closes that window and opens a new one, again with **Run as administrator**, to
pick up the `choco` command. Chocolatey installs Maven and adds it to the `PATH`:

```
choco install maven -y
```

### macOS

A student on a Mac installs the same JDK and Maven with [Homebrew](https://brew.sh), from Terminal:

```
brew install --cask temurin@25
brew install maven
```

### Checking the installation

The student opens a new terminal window and checks both versions:

```
java -version
mvn -version
```

The first line from `java` starts with `openjdk version "25`, and the first line from `mvn` starts
with `Apache Maven 3.9` or later.

## Building

A student runs these commands from the root of the repository:

| Task               | Command                         |
|--------------------|---------------------------------|
| Build and install  | `mvn clean install -DskipTests` |
| Run every test     | `mvn test`                      |
| Run one test class | `mvn test -Dtest=<ClassName>`   |
