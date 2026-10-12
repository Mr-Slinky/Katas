# Katas

Katas is a Java project that students download and extend. A student writes their own methods in a
copy of the code. Katas then runs those methods and shows each step of the run as a visual output.

## Trace Tables

The trace table is the first visual output planned for Katas. A trace table has one column for each
variable and each condition, and one row for each step of a method. Each cell shows the value that
variable or condition had at that step. A student reads down the table to follow how the method
changed its variables and whether each condition was true or false.

A student tracing the loop below by hand would write one row each time a variable changes or the
loop checks its condition. The code shows its line numbers down the left:

```java
1  int sum = 0;
2  for (int i = 1; i <= 3; i++) {
3      sum += i;
4  }
```

The trace table gives the line of each step in its first column and the statement run at that step
in its second. Katas fills in a variable's cell only on a row where its value changes, meaning a
blank cell keeps the value written above it. A condition's cell shows its result on each row where
the loop checks it:

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
The student runs both commands in a PowerShell window opened with **Run as administrator**:

```
winget install --id EclipseAdoptium.Temurin.25.JDK -e
winget install --id Chocolatey.Chocolatey -e
```

The student then closes that window and opens a new one, again with **Run as administrator**,
since only a new window finds the `choco` command on the `PATH`. The student then installs Maven
with Chocolatey, which adds Maven to the `PATH`:

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
with `Apache Maven` followed by version 3.9 or later.

## Building

A student runs these commands from the root of the repository:

| Task               | Command                         |
|--------------------|---------------------------------|
| Build and install  | `mvn clean install -DskipTests` |
| Run every test     | `mvn test`                      |
| Run one test class | `mvn test -Dtest=<ClassName>`   |

## Authorship

Each commit in the history of Katas records who wrote the code in it. Some commits contain work that
the maintainer and Claude, an AI assistant, did together. The author of such a commit is whoever did
most of the work. The other takes a `Co-Authored-By` line at the end of the commit message, which
marks a supporting role:

| Author         | Co-author      | Meaning                                                                                                                                                          |
|----------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The maintainer | Claude         | The maintainer wrote the code. Claude reviewed it or influenced it, for example through an audit, static analysis, a test run, or by teaching an API it uses. |
| Claude         | The maintainer | Claude wrote the code. The maintainer reviewed it and strongly influenced it.                                                                                    |

`git log --author=Claude` lists the commits that Claude did most of the work on.
