# Code Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One enforced Java style (finals, records, imports, formatting) across main and test, with every output and, where possible, the bytecode unchanged.

**Architecture:** First a bytecode fingerprint joins the existing golden-output check. Then Spotless with an Eclipse formatter config takes over layout, Checkstyle takes over the `final`/`var` rules, and two one-time passes apply what Checkstyle cannot express (field finality, class finality). Each step is proven by the checks below.

**Tech Stack:** Java 26, Gradle 9.8.1, Spotless Gradle plugin (Eclipse JDT formatter step), Gradle's built-in Checkstyle plugin.

**Spec:** `docs/notes/code-style-design.md`

## Global Constraints

- Pure style change: no behaviour, output, CLI, JSON or checkpoint change.
- Every task ends with BASELINE-CHECK and OUTPUT-CHECK printing `IDENTICAL`; Tasks S1 and S2 also need BYTECODE-CHECK `IDENTICAL`.
- Tool versions: the newest stable release of each tool that is at least two weeks old and parses every file in this repo; record the chosen versions in the task report.
- Write every config file for this repo; never copy files from other codebases.
- Comment rule (AGENTS.md) still applies; style tools must not reflow or reformat comments.
- Conventional Commits, explicit paths, never push.

**BASELINE-CHECK:**

```bash
./gradlew run -q --args="--units 100 --ticks 1000 --seed 42 --world 100 --out build/baseline-check/baseline.txt" && diff runs/baseline.txt build/baseline-check/baseline.txt && echo IDENTICAL
```

**OUTPUT-CHECK:**

```bash
rm -rf build/golden-now && scripts/golden-outputs.sh build/golden-now && diff -r .golden/ref build/golden-now && echo IDENTICAL
```

**BYTECODE-CHECK** (after Task S0 exists):

```bash
scripts/bytecode-fingerprint.sh build/bytecode-now.txt && diff .golden/bytecode-ref.txt build/bytecode-now.txt && echo IDENTICAL
```

## Review Focus

1. The formatter must not rewrap or reflow any comment, including the kept invariant Javadoc and the `// arrange` markers (checked in S1 by diffing comment lines).
2. `final` added to a parameter that is later reassigned would not compile; `final` must never be added by changing logic to avoid a reassignment (S2 review).
3. A field marked `final` in S3 must not change initialisation order or turn a computed static into a compile-time constant that changes output (OUTPUT-CHECK, plus the `javap` diff in S3).
4. A file the Eclipse formatter cannot parse must fail the build loudly, not be skipped silently (S1).
5. `./gradlew check` must fail on a deliberately unformatted file and on a deliberately non-final local (S4 smoke test).

---

### Task S0: Bytecode fingerprint and fresh references

**Files:**
- Create: `scripts/bytecode-fingerprint.sh`
- Modify: `build.gradle` (a `noDebug` project property that sets `options.debug = false` on every `JavaCompile` task)

**Interfaces:**
- Produces: `scripts/bytecode-fingerprint.sh <out-file>`, which runs `./gradlew -q clean compileJava compileTestJava -PnoDebug`, then writes one `sha256  relative/path.class` line per class file under `build/classes/java`, sorted by path. Also produces `.golden/bytecode-ref.txt` and a regenerated `.golden/ref`.

- [ ] **Step 1: Write the script and the `noDebug` switch.** Without `-PnoDebug` the build is unchanged.
- [ ] **Step 2: Prove reproducibility.** Run the script twice into `build/bc-a.txt` and `build/bc-b.txt`, then `diff`. Expected: no output.
- [ ] **Step 3: Prove it ignores formatting.** In a scratch copy of one source file, join two statements onto one line and reorder its imports; fingerprint; expect identical; revert the scratch change.
- [ ] **Step 4: Create references at the starting commit:** `scripts/bytecode-fingerprint.sh .golden/bytecode-ref.txt`, then `rm -rf .golden/ref && scripts/golden-outputs.sh .golden/ref`.
- [ ] **Step 5: BASELINE-CHECK, OUTPUT-CHECK, BYTECODE-CHECK.** Expected: `IDENTICAL` three times.
- [ ] **Step 6: Commit** `scripts/bytecode-fingerprint.sh`, `build.gradle`, `docs/notes/code-style-design.md`, `docs/notes/code-style-plan.md`: `chore: bytecode fingerprint for style-only changes`.

### Task S1: Spotless and the formatter

**Files:**
- Modify: `build.gradle` (Spotless plugin; `java` block with `eclipse().configFile('config/eclipse/formatter.xml')`, `importOrder`, `removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`, targets `src/*/java/**/*.java`)
- Create: `config/eclipse/formatter.xml`
- Modify: every Java file (formatting only, in its own commit)

**Interfaces:**
- Produces: `./gradlew spotlessCheck` and `./gradlew spotlessApply`; `spotlessCheck` runs as part of `./gradlew check`.

- [ ] **Step 1: Write `config/eclipse/formatter.xml`** for the spec's rules: spaces, indent 4, continuation 2 units (8 spaces), line width 120, `join_wrapped_lines=false`, comment and Javadoc formatting off, blank lines preserved up to 1. Records with 2 or more components force-wrap one per line with the closing parenthesis on its own line; an empty record body stays `) {}`. Import order: static first, then `java`, `javax`, `org`, `com`, the rest, blank line between groups.
- [ ] **Step 2: Wire Spotless in `build.gradle`** and commit config only: `build: spotless with an Eclipse formatter config`.
- [ ] **Step 3: Run `./gradlew spotlessApply`.** If any file fails to parse, stop and report BLOCKED with the file and construct. Never exclude a file to get past it.
- [ ] **Step 4: Check the shape.** `grep -rEn 'record [A-Z][A-Za-z0-9]*\([^)]*,[^)]*\)' src` finds nothing. Comment lines are unchanged: `git diff -U0 | grep -E '^[-+][[:space:]]*(//|\*|/\*\*)' ` shows no changed comment text other than pure indentation.
- [ ] **Step 5: BASELINE-CHECK, OUTPUT-CHECK, BYTECODE-CHECK, `./gradlew check`.** Expected: `IDENTICAL` three times; check passes.
- [ ] **Step 6: Commit** the formatted sources alone: `style: apply the formatter` (reviewers read it with whitespace changes hidden).

### Task S2: Checkstyle, final locals and parameters, no var

**Files:**
- Modify: `build.gradle` (Checkstyle plugin, `toolVersion`, `configFile = file('config/checkstyle/checkstyle.xml')`, `ignoreFailures = false`, `maxWarnings = 0`)
- Create: `config/checkstyle/checkstyle.xml`
- Modify: Java files that need `final` added to locals or parameters

**Interfaces:**
- Produces: `checkstyleMain` and `checkstyleTest`, both part of `./gradlew check`, with these modules:
  - `FinalLocalVariable` with `validateEnhancedForLoopVariable = false`
  - `FinalParameters` with tokens `METHOD_DEF, CTOR_DEF` (it skips interface and abstract methods; catch, enhanced-for and lambda parameters stay unchecked)
  - `MatchXpath` banning `var` as a declared type, message `no var: write the type`
  - `AvoidStarImport`, `RedundantImport`

- [ ] **Step 1: Add the config and plugin.** Run `./gradlew checkstyleMain checkstyleTest` and record the violation counts per module in the report.
- [ ] **Step 2: Add `final`** to every reported local and parameter. A variable that is reassigned keeps its assignment and stays non-final. Do not restructure code to avoid a reassignment.
- [ ] **Step 3: Re-run `./gradlew spotlessApply`** (added keywords can push lines past 120).
- [ ] **Step 4: BASELINE-CHECK, OUTPUT-CHECK, BYTECODE-CHECK, `./gradlew check`.** Expected: `IDENTICAL` three times; check passes with zero Checkstyle violations.
- [ ] **Step 5: Commit** config and plugin first (`build: checkstyle for final and var rules`), then the keyword additions (`style: final locals and parameters`).

### Task S3: Field and class finality

**Files:**
- Modify: Java files with never-reassigned non-final fields (27 private fields in main, 5 in test at the start of this plan), every `final class` that is not a static-only utility class, and every static-only utility class that is not yet final or lacks a private constructor
- Modify: `config/checkstyle/checkstyle.xml` (add `FinalClass` and `HideUtilityClassConstructor`)

**Interfaces:**
- Produces: the field and class rules of the spec, with Checkstyle guarding the utility-class half.

- [ ] **Step 1: Fields.** List every non-final field in main and test with whether it is reassigned after construction (table in the report). Make the never-reassigned ones `final`. Mutable state (swap buffers, accumulators, counters) stays non-final.
- [ ] **Step 2: Classes.** Remove `final` from every class that has instance state or instance methods. Static-only classes stay or become `final` with a private no-argument constructor. Records and enums are untouched.
- [ ] **Step 3: Add `FinalClass` and `HideUtilityClassConstructor`** to the Checkstyle config.
- [ ] **Step 4: Bytecode review.** Run `javap -p -v` over the classes compiled before and after this task (compiled with `-PnoDebug`) and diff. The only allowed differences are access flags (`ACC_FINAL`) and `ConstantValue` attributes on fields that became `static final`. Record any `ConstantValue` change and confirm OUTPUT-CHECK covers its use.
- [ ] **Step 5: BASELINE-CHECK, OUTPUT-CHECK, `./gradlew check`.** Expected: `IDENTICAL` twice; check passes. Then refresh the bytecode reference for later work: `scripts/bytecode-fingerprint.sh .golden/bytecode-ref.txt`.
- [ ] **Step 6: Commit:** `style: final fields, final only on utility classes`.

### Task S4: Docs and enforcement smoke test

**Files:**
- Modify: `AGENTS.md` (a "Code style" section stating the spec's rule table in one line per rule, plus `./gradlew spotlessApply` and `./gradlew check` in Commands)
- Create: `docs/decisions/0049-code-style.md` (context, decision, rejected: Lombok, palantir/google formatters that cannot put `)` on its own line, PMD/Error Prone for field finality)

- [ ] **Step 1: Smoke test.** Temporarily join two lines in one file and remove `final` from one local in another; `./gradlew check` must fail naming both. Revert.
- [ ] **Step 2: Write the docs.** ADR in 5 to 15 lines.
- [ ] **Step 3: BASELINE-CHECK, OUTPUT-CHECK, `./gradlew check`.** Expected: `IDENTICAL` twice; check passes.
- [ ] **Step 4: Commit:** `docs: code style rules and ADR`.
