# 0058: The Observatory replaces the old viewers

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** Watching, scrubbing and inspecting a run took `live.html` with `fzstd.min.js`, `scripts/serve.py` and `scripts/serve-live.sh` for live viewing, `tools/mapviz.py` to bake an offline page, and `--replay` with `SnapshotDump` and `tools/timetravel.py` to see one exact tick. The Observatory (ADR 0057) now covers all of it: live following, frame scrubbing, exact seek with the unit inspector and circuit, plus branch, re-run and compare.

**Decision.** Delete `live.html`, `fzstd.min.js`, `mapviz.py`, `timetravel.py`, `serve.py`, `serve-live.sh`, `ReplayCommand`, `SnapshotDump`, `--replay` and their tests. `MapFrameWriter`, the frame format and `RunDirectoryReader.seek` stay: the Observatory reads them.

**Why.** Two viewers drift apart and double every format change. One tool is the point of the milestone.

**Rejected.** Keeping `mapviz.py` for an offline single-file export: nobody asked for it since the live viewer existed, and it can come back as an Observatory export if missed. Keeping `--replay` as a CLI seek: the Observatory's seek is the same code with a view on top.
