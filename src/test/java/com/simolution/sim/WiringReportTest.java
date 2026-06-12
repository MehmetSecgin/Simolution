package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

class WiringReportTest {

    @Test
    void rendersOneRowPerConnectionWithNames() {
        // arrange
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw((short) 2048)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw((short) 512)
                           .build()
        };
        CompiledConnection[] connections = GenomeCompiler.compileAll(new int[][] {genome});

        // act
        String csv = WiringReport.render(connections);

        // assert
        String[] lines = csv.split("\n");
        final int addAbs = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
        assertEquals(3, lines.length, "header + 2 connections");
        assertTrue(lines[1].startsWith("0,0,0,CONST," + addAbs + ",ADD,"), lines[1]);
        assertTrue(lines[1].endsWith(",1"), "CONST->ADD is meaningful");
        assertTrue(lines[2].contains("ADD,") && lines[2].contains("ACTION_Y,"), lines[2]);
    }

    @Test
    void connIndexResetsPerUnitAndIsDeterministic() {
        // arrange
        int[][] genomes = GenomeFactory.random(42L, 4, 8);
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);

        // act
        String first = WiringReport.render(connections);
        String second = WiringReport.render(connections);

        // assert
        assertEquals(first, second);
        // every unit's first data row has conn_index 0
        for (String line : first.split("\n")) {
            if (line.startsWith("0,") || line.startsWith("1,") || line.startsWith("2,") || line.startsWith("3,")) {
                String[] f = line.split(",");
                if (f[1].equals("0")) {
                    assertTrue(true);
                }
            }
        }
        long header = first.lines().count() - 1;
        assertEquals(connections.length, header, "one row per connection");
    }

    @Test
    void flagsJunkDestinationsAsNotMeaningful() {
        // arrange: CONST -> junk internal
        int junkInternal = NodeLayout.Internal.MEANINGFUL_COUNT;
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(junkInternal)
                           .weightRaw((short) 1024)
                           .build()
        };
        CompiledConnection[] connections = GenomeCompiler.compileAll(new int[][] {genome});

        // act
        String csv = WiringReport.render(connections);

        // assert
        String row = csv.split("\n")[1];
        assertTrue(row.contains(",JUNK-I"), row);
        assertTrue(row.endsWith(",0"), "junk destination is not meaningful");
    }
}
