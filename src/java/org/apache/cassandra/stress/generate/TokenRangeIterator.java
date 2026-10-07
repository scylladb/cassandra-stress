// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.cassandra.stress.driver.TokenSlice;
import org.apache.cassandra.stress.settings.StressSettings;

public class TokenRangeIterator {
    private final Collection<TokenSlice> tokenRanges;
    private final ConcurrentLinkedQueue<TokenSlice> pendingRanges;
    private final boolean wrap;

    public TokenRangeIterator(StressSettings settings, Collection<TokenSlice> tokenRanges) {
        this.tokenRanges = maybeSplitRanges(tokenRanges, settings.tokenRange.splitFactor);
        this.pendingRanges = new ConcurrentLinkedQueue<>(this.tokenRanges);
        this.wrap = settings.tokenRange.wrap;
    }

    private static Collection<TokenSlice> maybeSplitRanges(Collection<TokenSlice> tokenRanges, int splitFactor) {
        if (splitFactor <= 1) return tokenRanges;

        TreeSet<TokenSlice> ret = new TreeSet<>();
        for (TokenSlice range : tokenRanges) ret.addAll(range.splitEvenly(splitFactor));

        return List.copyOf(ret);
    }

    public void update() {
        if (wrap && pendingRanges.isEmpty()) pendingRanges.addAll(tokenRanges);
    }

    public TokenSlice next() {
        return pendingRanges.poll();
    }

    public boolean exhausted() {
        return pendingRanges.isEmpty();
    }
}
