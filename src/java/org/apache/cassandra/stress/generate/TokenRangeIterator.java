// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.datastax.driver.core.TokenRange;
import org.apache.cassandra.stress.settings.StressSettings;

public class TokenRangeIterator
{
    private final Set<TokenRange> tokenRanges;
    private final ConcurrentLinkedQueue<TokenRange> pendingRanges;
    private final boolean wrap;

    public TokenRangeIterator(StressSettings settings, Set<TokenRange> tokenRanges)
    {
        this.tokenRanges = maybeSplitRanges(tokenRanges, settings.tokenRange.splitFactor);
        this.pendingRanges = new ConcurrentLinkedQueue<>(this.tokenRanges);
        this.wrap = settings.tokenRange.wrap;
    }

    private static Set<TokenRange> maybeSplitRanges(Set<TokenRange> tokenRanges, int splitFactor)
    {
        if (splitFactor <= 1)
            return tokenRanges;

        Set<TokenRange> ret = new TreeSet<>();
        for (TokenRange range : tokenRanges)
            ret.addAll(range.splitEvenly(splitFactor));

        return ret;
    }

    public void update()
    {
        if (wrap && pendingRanges.isEmpty())
            pendingRanges.addAll(tokenRanges);
    }

    public TokenRange next()
    {
        return pendingRanges.poll();
    }

    public boolean exhausted()
    {
        return pendingRanges.isEmpty();
    }
}
