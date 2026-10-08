package org.apache.cassandra.stress.generate.values;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.apache.cassandra.stress.settings.OptionDistribution;
import org.junit.jupiter.api.Test;

class CollectionsTest {
    private static GeneratorConfig config(String name) {
        return new GeneratorConfig("seed for stress" + name, null, OptionDistribution.get("uniform(1..20)"), null);
    }

    @Test
    void aListIsTheSameForTheSameSeed() {
        Lists<Integer> lists = new Lists<>("nums", new Integers("nums", config("nums")), config("nums"));
        lists.setSeed(42);
        List<Integer> first = lists.generate();
        for (long seed = 0; seed < 50; seed++) {
            lists.setSeed(seed);
            lists.generate();
        }
        lists.setSeed(42);
        assertEquals(first, lists.generate());
    }

    @Test
    void aSetIsTheSameForTheSameSeed() {
        Sets<String> sets = new Sets<>("tags", new Strings("tags", config("tags")), config("tags"));
        sets.setSeed(7);
        Set<String> first = sets.generate();
        for (long seed = 0; seed < 50; seed++) {
            sets.setSeed(seed);
            sets.generate();
        }
        sets.setSeed(7);
        assertEquals(first, sets.generate());
    }
}
