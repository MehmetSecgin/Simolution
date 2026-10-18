package com.simolution.kernel.layout;

import java.util.List;

import com.simolution.kernel.layout.NodeSpec.Kind;
import com.simolution.kernel.layout.NodeSpec.Role;

/**
 * The ordered list of meaningful nodes per block plus the junk padding that
 * follows each block. {@link NodeLayout} derives every index, offset and count
 * from one catalog, so adding a node here is the whole change to the layout.
 */
public class NodeCatalog {

    public static final NodeCatalog V5 = new NodeCatalog(
            List.of(
                    NodeSpec.node("CONST", Role.CONST),
                    NodeSpec.node("RAND", Role.RAND),
                    NodeSpec.chemicalSensor("LOCAL_RESOURCE", 0),
                    NodeSpec.node("SELF_ENERGY", Role.SELF_ENERGY),
                    NodeSpec.node("SELF_MASS", Role.SELF_MASS)),
            List.of(
                    NodeSpec.named("ADD"),
                    NodeSpec.named("MUL"),
                    NodeSpec.named("CLAMP"),
                    NodeSpec.named("DELAY"),
                    NodeSpec.named("THRESH")),
            List.of(
                    NodeSpec.named("ACTION_Y"),
                    NodeSpec.named("HARVEST"),
                    NodeSpec.named("REPRODUCE"),
                    NodeSpec.named("MOVE_N"),
                    NodeSpec.named("MOVE_S"),
                    NodeSpec.named("MOVE_E"),
                    NodeSpec.named("MOVE_W"),
                    NodeSpec.named("GROW")),
            2, 3, 1);

    private final List<NodeSpec> sensors;
    private final List<NodeSpec> internals;
    private final List<NodeSpec> actions;
    private final int sensorJunk;
    private final int internalJunk;
    private final int actionJunk;

    private NodeCatalog(final List<NodeSpec> sensors, final List<NodeSpec> internals, final List<NodeSpec> actions,
            final int sensorJunk, final int internalJunk, final int actionJunk) {
        this.sensors = sensors;
        this.internals = internals;
        this.actions = actions;
        this.sensorJunk = sensorJunk;
        this.internalJunk = internalJunk;
        this.actionJunk = actionJunk;
    }

    public List<NodeSpec> nodes(final Kind kind) {
        return switch (kind) {
            case SENSOR -> sensors;
            case INTERNAL -> internals;
            case ACTION -> actions;
        };
    }

    public int junk(final Kind kind) {
        return switch (kind) {
            case SENSOR -> sensorJunk;
            case INTERNAL -> internalJunk;
            case ACTION -> actionJunk;
        };
    }

    public int indexOf(final Kind kind, final String name) {
        final List<NodeSpec> block = nodes(kind);
        for (int i = 0; i < block.size(); i++) {
            if (block.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no " + kind + " node named " + name);
    }

}
