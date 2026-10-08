# CLAUDE.md

This file guides AI agents working in the Katas repository. The global CLAUDE.md still applies,
apart from its sections on Spotless, Checkstyle, PMD, JaCoCo and Log4j2. The build uses Maven
Enforcer, the compiler plugin and Surefire alone.

## Project

Katas is a Java project that students download and extend. A student writes their own methods
in their copy of the code. Katas then runs those methods and shows each step of the run as a
visual output.

The first visual output is the trace table, which README.md describes in full. The maintainer has
decided to add further visual outputs after it.

## Build

Katas is a single Maven module. README.md lists the Java and Maven versions it requires and the
commands that build and test it, which agents run from the repository root.

## AI policy

This policy covers everyone who works in this repository: the maintainer of Katas,
and each student who extends a downloaded copy. Wherever this file says "the developer", it means
whichever of those two is at the keyboard. Agents follow this policy in every session, whatever the
request.

The developer wants to understand and own every line of Katas. The developer writes the code,
and AI agents help with that understanding and act as peer reviewers.

### Discussion and instructions

The agent answers a message that discusses an idea, a design or a problem in conversation alone.
The agent starts building only when a message asks for it in plain words, such as "write",
"implement", "fix" or "commit". Where a message could be either, the agent treats it as discussion.

| Message                                                    | Treat as    |
|------------------------------------------------------------|-------------|
| "I'm wondering whether each step should be a record."      | Discussion  |
| "How would you capture a variable's value inside a loop?"  | Discussion  |
| "Write a method that adds one row to the trace table."     | Instruction |

### Areas open to agents

Agents may act without asking in three areas:

1. **Documentation and comments.** Agents write and edit Javadoc, inline comments, READMEs and
   anything under `docs/`.
2. **One method at a time.** An agent writes a single, clearly defined method per request. The
   request states what the method takes, what it returns and what it does. Where any of those is
   unclear, the agent asks first. After writing the method, the agent explains the Java concepts it
   relies on, then stops.
3. **Git.** Agents write every commit message, the developer's included. An agent commits its own
   changes as the Commits section sets out. Each time the developer asks, it commits the
   developer's work, pushes, or pulls.

An agent makes every other change only on an explicit instruction from the developer. That includes writing more
than one method, adding classes, packages or modules, restructuring code, adding dependencies and
changing the logic of code the developer wrote.

### Code review

When the developer asks for a review of their code, the review exists to improve the developer's
Java.

- The agent orders findings by importance: bugs first, then non-idiomatic Java, then style.
- Each finding states the problem and the Java reason behind it, such as references and mutation,
  `equals` and `hashCode`, generics, or a standard-library idiom.
- A finding can include a short snippet in chat that shows the fix. The developer then makes the
  change in the code.

## Commits

Commits follow the Conventional Commits format from the `technical-writing` skill.

The developer's work and an agent's work go in separate commits. The git author field on each commit
records whose work it contains:

| Work            | Author                                 |
|-----------------|----------------------------------------|
| The developer's | The developer, as `git config` sets it |
| An agent's      | `Claude <noreply@anthropic.com>`       |

An agent commits its own changes only after the developer has reviewed them and says to commit.
Until then, the agent reports that the change is ready for review and stops. At commit time, the
agent stages only the files it changed, each by name, meaning each commit contains one person's
work. It also sets the author explicitly:

```
git commit --author="Claude <noreply@anthropic.com>" -m "docs: explain what a trace table shows"
```

Git then records Claude as the author and the developer as the committer. `git log --author=Claude`
lists every agent commit.

A review suggestion that the developer applies in the code is the developer's work. As such, it goes
in a developer commit.
