# 0057: The Observatory is an HTTP server inside the engine's JVM

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** ADR 0031 removed the bespoke `LiveServer`: a viewer only reads files, so any static server with Range support was enough. The Observatory also starts, pauses, stops, branches and re-runs runs, and it seeks any tick, which needs a kernel.

**Decision.** `com.simolution.observatory.Observatory` is a `com.sun.net.httpserver` server bound to loopback, run with `./gradlew observatory`. It owns a `RunManager` that runs one run at a time on all cores and queues the rest, a `Seeker` that keeps up to 4 viewer kernels on half the cores, and an `EventStream` that pushes `run` and `progress` server-sent events. Every run it starts writes the same run directory as the CLI (run-directory-v1), so the CLI and the server read each other's runs.

**Why.** ADR 0031 was right for viewing, but control needs the engine. In one JVM a pause is a flag, a live frame is a buffer copy, and a seek reuses a warm kernel. No dependency is added.

**Rejected.** A Python server driving the CLI as subprocesses: pause, live frames and seek would each need a protocol over pipes, and every seek would pay a JVM start. WebSocket: the browser only listens, so server-sent events over plain HTTP do the job without a handshake library.
