# 0049: Code style enforced by the build

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Spec:** [code-style-design](../notes/code-style-design.md)

**Context.** The code had no single style and nothing stopped it drifting. The owner wanted `final` everywhere it applies, no `var`, one record shape and one layout, held by the build.

**Decision.** Spotless 8.10.3 with the Eclipse JDT 4.40 formatter (`config/eclipse/formatter.xml`) owns layout, imports and trailing whitespace. Checkstyle 14.3.0 (`config/checkstyle/checkstyle.xml`) owns `FinalLocalVariable`, `FinalParameters`, `HideUtilityClassConstructor`, `AvoidStarImport`, `RedundantImport` and a `MatchXpath` ban on `var`. `./gradlew check` runs both. The rules are one line each in AGENTS.md "Code style". Both configs were written for this repo.

**Why Eclipse JDT.** It is the one formatter that can put `)` of a record on its own line, one component per line.

**Rulings.** `final` on locals initialised with a constant makes javac inline the constant, so 5 test classes compile to different bytecode; output stays identical. Accepted by ruling; hoisting those locals to constants stays open. Checkstyle `FinalClass` is not used: it demands `final` on every class whose constructors are all private, not only on utility classes. Two reassigned parameters (`Noise.mix`, a test helper) keep `@SuppressWarnings("checkstyle:FinalParameters")` instead of being rewritten. No field qualified for `final`: javac rejected every candidate.

**Rejected.**
- Lombok: the owner does not want it, and records already cover the data classes.
- palantir-java-format and google-java-format: neither can put `)` on its own line for records.
- PMD or Error Prone for field finality: another tool and rule set for a rule that found nothing to change, so it stays in AGENTS.md and review.
