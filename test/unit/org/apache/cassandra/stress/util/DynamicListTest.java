package org.apache.cassandra.stress.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DynamicListTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchesAListUnderRandomAppendsAndRemoves(boolean locked) {
        DynamicList<Integer> list = locked ? new LockedDynamicList<>(1_000) : new DynamicList<>(1_000);
        List<Integer> model = new ArrayList<>();
        List<DynamicList.Node<Integer>> nodes = new ArrayList<>();
        Random random = new Random(1);
        for (int step = 0; step < 5_000; step++) {
            if (model.isEmpty() || random.nextInt(3) > 0) {
                nodes.add(list.append(step));
                model.add(step);
            } else {
                int index = random.nextInt(model.size());
                list.remove(nodes.remove(index));
                model.remove(index);
            }
            assertEquals(model.size(), list.size());
            if (!model.isEmpty()) {
                int probe = random.nextInt(model.size());
                assertEquals(model.get(probe), list.get(probe));
            }
        }
        for (int i = 0; i < model.size(); i++) assertEquals(model.get(i), list.get(i));
    }

    @Test
    void appendStopsAtTheMaximumSize() {
        DynamicList<String> list = new DynamicList<>(4);
        list.append("a", 2);
        list.append("b", 2);
        assertNull(list.append("c", 2));
        assertEquals(2, list.size());
        assertNull(list.get(2));
    }
}
