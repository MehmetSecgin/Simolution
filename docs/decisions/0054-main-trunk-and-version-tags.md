# 0054: `main` trunk, one tag per kernel version

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6

**Context.** Each milestone had its own long-lived branch, and the newest one was the GitHub default. Visitors landed on a different branch every milestone, the default had to be switched by hand, and a frozen branch could still take commits. The web app will need one stable branch to deploy from.

**Decision.** `main` is the trunk and the GitHub default, created from the `kernel-v6-engine` tip. Every finished milestone gets an annotated tag on its tip: `v0.1`, `v1` to `v6`. The old milestone branches stay on `origin`, frozen, so existing links keep working.

**Why tags.** A tag marks one point in history and never moves; a branch says work happens there. GitHub lists tags as releases, the natural place for a milestone summary.

**Rejected.** Frozen branches per version on top of `main`: they can drift and clutter the branch list. Deleting the old branches now: links to them would break; that is a later, separate call.
