// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record TokenSlice(long start, long end) implements Comparable<TokenSlice> {
    private static final BigInteger RING_END = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger RING_LENGTH = RING_END.subtract(BigInteger.valueOf(Long.MIN_VALUE));

    public static List<TokenSlice> sortedAndUnwrapped(Collection<TokenSlice> ranges) {
        List<TokenSlice> result = new ArrayList<>(ranges.size() + 1);
        for (TokenSlice range : ranges) {
            result.addAll(range.unwrap());
        }
        result.sort(null);
        return result;
    }

    public boolean isWrappedAround() {
        return start > end && end != Long.MIN_VALUE;
    }

    public boolean endsAtRingEnd() {
        return end == Long.MIN_VALUE;
    }

    public List<TokenSlice> unwrap() {
        if (start == end && start != Long.MIN_VALUE) {
            return List.of(new TokenSlice(start, Long.MIN_VALUE), new TokenSlice(Long.MIN_VALUE, end));
        }
        if (!isWrappedAround()) {
            return List.of(this);
        }
        List<TokenSlice> parts = new ArrayList<>(2);
        if (start != Long.MIN_VALUE) {
            parts.add(new TokenSlice(start, Long.MIN_VALUE));
        }
        parts.add(new TokenSlice(Long.MIN_VALUE, end));
        return parts;
    }

    public List<TokenSlice> splitEvenly(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("The number of parts must be positive: " + parts);
        }
        BigInteger first = BigInteger.valueOf(start);
        BigInteger range = BigInteger.valueOf(end).subtract(first);
        if (range.signum() <= 0) {
            range = range.add(RING_LENGTH);
        }
        BigInteger[] quotient = range.divideAndRemainder(BigInteger.valueOf(parts));
        int remainder = quotient[1].intValue();
        List<TokenSlice> result = new ArrayList<>(parts);
        BigInteger current = first;
        long previous = start;
        for (int i = 1; i < parts; i++) {
            current = current.add(remainder-- > 0 ? quotient[0].add(BigInteger.ONE) : quotient[0]);
            if (current.compareTo(RING_END) > 0) {
                current = current.subtract(RING_LENGTH);
            }
            result.add(new TokenSlice(previous, current.longValue()));
            previous = current.longValue();
        }
        result.add(new TokenSlice(previous, end));
        return result;
    }

    public String format() {
        return "[" + start + ", " + end + "]";
    }

    @Override
    public int compareTo(TokenSlice other) {
        int byStart = Long.compare(start, other.start);
        return byStart != 0 ? byStart : Long.compare(end, other.end);
    }
}
