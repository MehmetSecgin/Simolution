package com.simolution.observatory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunDirectoryReader;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

public class RunStore {

    private static final Pattern DIR_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Set<RunState> ACTIVE = Set.of(RunState.QUEUED, RunState.RUNNING, RunState.PAUSED);
    private static final int MAX_SLUG = 40;

    private final Path root;

    public RunStore(final Path root) {
        this.root = root;
    }

    public List<RunEntry> list() throws IOException {
        final List<RunEntry> entries = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.sorted().toList()) {
                if (!Files.isRegularFile(new RunPaths(dir).manifest())) {
                    continue;
                }
                final String name = dir.getFileName().toString();
                entries.add(DIR_NAME.matcher(name).matches()
                        ? read(dir)
                        : new RunEntry(name, null, null, Files.getLastModifiedTime(dir).toMillis(),
                                "the directory name is not a valid run name"));
            }
        }
        return entries;
    }

    public RunEntry get(final String dir) throws IOException {
        return read(path(dir));
    }

    public Path path(final String dir) {
        if (dir == null || !DIR_NAME.matcher(dir).matches()
                || !Files.isRegularFile(new RunPaths(root.resolve(dir)).manifest())) {
            throw new NoSuchElementException("no run named " + dir);
        }
        return root.resolve(dir);
    }

    public Path newDir(final String name) throws IOException {
        final String slug = slug(name);
        for (int n = 1;; n++) {
            try {
                return Files.createDirectory(root.resolve(slug + "-" + n));
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
        }
    }

    public RunEntry findByRunId(final String runId) throws IOException {
        RunEntry twin = null;
        for (RunEntry entry : list()) {
            if (entry.manifest() != null && runId.equals(entry.manifest().runId())) {
                if (entry.manifest().twinOf() == null) {
                    return entry;
                }
                twin = twin == null ? entry : twin;
            }
        }
        return twin;
    }

    public void delete(final String dir) throws IOException {
        final RunEntry entry = get(dir);
        if (entry.status() != null && ACTIVE.contains(entry.status().status())) {
            throw new IllegalStateException(dir + " is running; stop it first");
        }
        for (RunEntry other : list()) {
            if (other.manifest() != null && dir.equals(other.manifest().parent())) {
                throw new IllegalStateException(dir + " has branches; delete them first");
            }
        }
        try (Stream<Path> files = Files.walk(path(dir))) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    public void markInterrupted() throws IOException {
        for (RunEntry entry : list()) {
            if (entry.status() != null && ACTIVE.contains(entry.status().status())) {
                final Path dir = root.resolve(entry.dir());
                new RunStatus(RunState.STOPPED, lastCheckpoint(dir), null, null).write(new RunPaths(dir).status());
            }
        }
    }

    private static int lastCheckpoint(final Path dir) throws IOException {
        try {
            return RunDirectoryReader.checkpointTickAtOrBefore(dir, Integer.MAX_VALUE);
        } catch (IllegalArgumentException none) {
            return 0;
        }
    }

    private RunEntry read(final Path dir) throws IOException {
        final RunPaths paths = new RunPaths(dir);
        final String name = dir.getFileName().toString();
        final long modified = Files.getLastModifiedTime(dir).toMillis();
        final RunManifest manifest;
        try {
            manifest = RunManifest.read(paths.manifest());
        } catch (IOException e) {
            return new RunEntry(name, null, null, modified, "run.json: " + e.getMessage());
        }
        if (!Kernel.ENGINE_VERSION.equals(manifest.engine())) {
            return new RunEntry(name, manifest, null, modified,
                    "engine " + manifest.engine() + ", this build runs " + Kernel.ENGINE_VERSION);
        }
        try {
            return new RunEntry(name, manifest, RunStatus.read(paths.status()), modified, null);
        } catch (IOException e) {
            return new RunEntry(name, manifest, null, modified, "status.json: " + e.getMessage());
        }
    }

    private static String slug(final String name) {
        final String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String slug = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "run" : slug;
    }
}
