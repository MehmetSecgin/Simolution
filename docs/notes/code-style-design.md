# Code style (after the readability refactor)

## Intent

The owner wants one consistent Java style, modelled on the house style of their day-job codebase (measured across 24 services) but adapted to a codebase with no Spring and no Lombok. Style must be enforced by the build so it cannot drift.

## Rules

| Topic | Rule |
|---|---|
| `final` on local variables | always, main and test |
| `final` on parameters | always on methods and constructors that have a body; never on interface or abstract methods. A parameter the method reassigns stays non-final and carries `@SuppressWarnings("checkstyle:FinalParameters")` |
| `final` on fields | every field that is never reassigned after construction |
| `final` on catch parameters, enhanced-for variables, lambda parameters, methods | never |
| `final` on classes | only on static-only utility classes, which also get a private constructor; no other class is final |
| `var` | never, main and test; tests use explicit `final Type name` |
| Records | 2 or more components: one component per line, `)` on its own line; an empty body is written `) {}`. A single component may stay on one line |
| Formatting | 4-space indent, 8-space continuation, 120 columns, no tabs; the formatter never joins lines a human wrapped; comment formatting off |
| Imports | static imports first, then `java`, `javax`, `org`, `com`, everything else, each group separated by a blank line; no wildcards; no unused imports |
| Test names | sentence style stays (`observersRunInArrayOrderEveryTick`); contract references in `@DisplayName` |
| Lombok | not used |

## Enforcement

- Spotless with the Eclipse JDT formatter (config written for this repo, not copied from elsewhere) owns layout, import order, unused imports, trailing whitespace and the final newline.
- Checkstyle owns the `final` and `var` rules it can express: `FinalLocalVariable`, `FinalParameters`, `HideUtilityClassConstructor`, `AvoidStarImport`, `RedundantImport`, and a `MatchXpath` ban on `var`.
- Field finality, class finality and the never-final cases (catch, enhanced-for, lambda parameters, methods) are not expressible in Checkstyle; they are applied once and kept by the AGENTS.md rule and review.
- `./gradlew check` runs all of it.

## Constraints

- Pure style change: every output byte stays identical (`scripts/golden-outputs.sh`, `runs/baseline.txt` with `state-digest: 78644e65e8086e45`).
- Formatting, import changes, `final` on locals and parameters, and `var` removal must leave the compiled bytecode identical when compiled without debug info, except where a `final` local initialised with a constant becomes a compile-time constant and javac inlines it.
- Tool versions: newest stable releases at least two weeks old that parse the Java 26 code in this repo.
- Company code and config files are never copied into this public repo.
