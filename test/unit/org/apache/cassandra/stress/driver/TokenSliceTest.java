package org.apache.cassandra.stress.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.oss.driver.api.core.metadata.token.TokenRange;
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token;
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TokenSliceTest {
    private static TokenRange driver(long start, long end) {
        return new Murmur3TokenRange(new Murmur3Token(start), new Murmur3Token(end));
    }

    private static List<TokenSlice> slices(List<TokenRange> ranges) {
        return ranges.stream()
                .map(r -> new TokenSlice(
                        ((Murmur3Token) r.getStart()).getValue(), ((Murmur3Token) r.getEnd()).getValue()))
                .toList();
    }

    static Stream<Arguments> ranges() {
        Random random = new Random(42);
        Stream<Arguments> fixed = Stream.of(
                Arguments.of(-100L, 100L),
                Arguments.of(100L, -100L),
                Arguments.of(Long.MIN_VALUE, 0L),
                Arguments.of(0L, Long.MIN_VALUE),
                Arguments.of(Long.MAX_VALUE, Long.MIN_VALUE + 1),
                Arguments.of(5L, 5L));
        Stream<Arguments> randomRanges = Stream.generate(() -> Arguments.of(random.nextLong(), random.nextLong()))
                .limit(50);
        return Stream.concat(fixed, randomRanges);
    }

    @ParameterizedTest
    @MethodSource("ranges")
    void unwrapsAsTheDriverDoes(long start, long end) {
        TokenSlice slice = new TokenSlice(start, end);
        assertEquals(driver(start, end).isWrappedAround(), slice.isWrappedAround());
        assertEquals(slices(driver(start, end).unwrap()), slice.unwrap());
    }

    @ParameterizedTest
    @MethodSource("ranges")
    void splitsEvenlyAsTheDriverDoes(long start, long end) {
        for (int parts : new int[] {1, 2, 3, 7}) {
            if (start == end) continue;
            assertEquals(
                    slices(driver(start, end).splitEvenly(parts)),
                    new TokenSlice(start, end).splitEvenly(parts),
                    "parts=" + parts);
        }
    }

    @Test
    void sortsAndUnwrapsTheRing() {
        List<TokenSlice> ring = TokenSlice.sortedAndUnwrapped(
                List.of(new TokenSlice(100, -100), new TokenSlice(-100, 0), new TokenSlice(0, 100)));
        assertEquals(
                List.of(
                        new TokenSlice(Long.MIN_VALUE, -100),
                        new TokenSlice(-100, 0),
                        new TokenSlice(0, 100),
                        new TokenSlice(100, Long.MIN_VALUE)),
                ring);
        assertTrue(ring.stream().noneMatch(TokenSlice::isWrappedAround));
    }

    @Test
    void formatsTheBounds() {
        assertEquals("[-5, 7]", new TokenSlice(-5, 7).format());
        assertFalse(new TokenSlice(-5, 7).isWrappedAround());
    }
}
