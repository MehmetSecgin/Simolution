package com.simolution.kernel.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class NodeLayoutTest {

    @Test
    void layoutMatchesV5() {
        // assert
        assertEquals(24, NodeLayout.TOTAL);
        assertEquals(7, NodeLayout.INTERNAL_OFFSET);
        assertEquals(15, NodeLayout.ACTION_OFFSET);
        assertEquals("LOCAL_RESOURCE", NodeLayout.localName(2));
        assertEquals("JUNK-S5", NodeLayout.localName(5));
        assertEquals(7, NodeLayout.Action.GROW);
        assertFalse(NodeLayout.isMeaningful(5));
        assertEquals(NodeSpec.Role.CHEMICAL_HERE, NodeCatalog.V5.nodes(NodeSpec.Kind.SENSOR).get(2).role());
    }

    @Test
    void jsonListsEveryNode() throws Exception {
        // act
        final JsonNode json = new ObjectMapper().readTree(NodeLayout.toJson());

        // assert
        assertEquals(24, json.get("names").size());
        for (int i = 0; i < NodeLayout.TOTAL; i++) {
            assertEquals(NodeLayout.localName(i), json.get("names").get(i).asText());
        }
        assertEquals(15, json.get("offsets").get("action").asInt());
        assertEquals(9, json.get("typeCounts").get("action").asInt());
        assertEquals(23, json.get("junk").get("action").get(0).asInt());
        assertEquals(24, json.get("junk").get("action").get(1).asInt());
    }
}
