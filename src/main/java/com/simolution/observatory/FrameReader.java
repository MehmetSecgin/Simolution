package com.simolution.observatory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.luben.zstd.Zstd;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.ResourceLevels;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunDirectoryReader;

public class FrameReader {

    private static final String FORMAT = "format v16 ";

    private record Chunk(
            String text,
            int width
    ) {}

    private final Map<String, Chunk> chunks = new LinkedHashMap<>(16, 0.75f, true);
    private final long budgetBytes;
    private long cachedBytes;

    public FrameReader(final long budgetBytes) {
        this.budgetBytes = budgetBytes;
    }

    public byte[] frame(final Path runDir, final int tick) throws IOException {
        final RunPaths paths = new RunPaths(RunDirectoryReader.holderOf(runDir, tick));
        if (!Files.exists(paths.framesIdx())) {
            return null;
        }
        final List<String> index = Files.readAllLines(paths.framesIdx());
        if (index.isEmpty() || !index.get(0).startsWith(FORMAT)) {
            return null;
        }
        final int width = Integer.parseInt(index.get(0).split(" ")[3]);
        for (String row : index.subList(1, index.size())) {
            final String[] f = row.split(" ");
            if (f.length == 5 && Integer.parseInt(f[0]) <= tick && tick <= Integer.parseInt(f[1])) {
                final Chunk chunk = chunk(paths.framesZst(), Long.parseLong(f[2]), Integer.parseInt(f[3]), width);
                return parse(chunk, tick);
            }
        }
        return null;
    }

    public static byte[] encode(final WorldState state, final EngineConfig config) {
        final int cells = state.worldWidth * state.worldWidth;
        final double capacity = config.chemicals().get(0).capacity();
        final byte[] levels = new byte[cells];
        for (int cell = 0; cell < cells; cell++) {
            levels[cell] = (byte) ResourceLevels.level(state.cellChem[cell], capacity);
        }
        int alive = 0;
        for (int slot = 0; slot < state.unitCount; slot++) {
            if (state.energy[slot] > 0.0) {
                alive++;
            }
        }
        final ByteBuffer out = header(state.tick, state.worldWidth, levels, alive);
        for (int slot = 0; slot < state.unitCount; slot++) {
            if (state.energy[slot] > 0.0) {
                out.putInt(state.position[slot]).putInt(slot).putInt((int) state.lineageId[slot]);
            }
        }
        return out.array();
    }

    static ByteBuffer header(final int tick, final int width, final byte[] levels, final int units) {
        final ByteBuffer out = ByteBuffer.allocate(4 + 4 + levels.length + 4 + 12 * units)
                .order(ByteOrder.LITTLE_ENDIAN);
        return out.putInt(tick).putInt(width).put(levels).putInt(units);
    }

    private synchronized Chunk chunk(final Path zst, final long offset, final int length, final int width)
            throws IOException {
        final BasicFileAttributes attributes = Files.readAttributes(zst, BasicFileAttributes.class);
        final String key = zst + "@" + attributes.fileKey() + "@" + attributes.creationTime() + "@" + offset;
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            final byte[] compressed = new byte[length];
            try (RandomAccessFile file = new RandomAccessFile(zst.toFile(), "r")) {
                file.seek(offset);
                file.readFully(compressed);
            }
            final byte[] raw = Zstd.decompress(compressed, (int) Zstd.getFrameContentSize(compressed));
            chunk = new Chunk(new String(raw, StandardCharsets.ISO_8859_1), width);
            if (raw.length <= budgetBytes) {
                chunks.put(key, chunk);
                cachedBytes += raw.length;
                final Iterator<Chunk> eldest = chunks.values().iterator();
                while (cachedBytes > budgetBytes) {
                    cachedBytes -= eldest.next().text().length();
                    eldest.remove();
                }
            }
        }
        return chunk;
    }

    private static byte[] parse(final Chunk chunk, final int tick) {
        final String text = chunk.text();
        final String marker = "t " + tick + "\n";
        final int start = text.startsWith(marker) ? 0 : text.indexOf("\n" + marker) + 1;
        if (!text.startsWith(marker, start)) {
            return null;
        }
        final int next = text.indexOf("\nt ", start);
        final String[] lines = text.substring(start, next < 0 ? text.length() : next + 1).split("\n");
        final byte[] levels = new byte[chunk.width() * chunk.width()];
        int cell = 0;
        for (String run : lines[1].substring(1).trim().split(" ")) {
            final int x = run.indexOf('x');
            final byte level = (byte) (run.charAt(0) - '0');
            final int count = Integer.parseInt(run.substring(x + 1));
            for (int i = 0; i < count; i++) {
                levels[cell++] = level;
            }
        }
        final ByteBuffer out = header(tick, chunk.width(), levels, lines.length - 2);
        for (int i = 2; i < lines.length; i++) {
            final String[] u = lines[i].split(" ");
            out.putInt(Integer.parseInt(u[1])).putInt(Integer.parseInt(u[2])).putInt(Integer.parseInt(u[4]));
        }
        return out.array();
    }
}
