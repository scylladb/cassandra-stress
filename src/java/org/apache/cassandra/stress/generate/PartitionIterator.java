// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.cassandra.stress.generate.values.Generator;
import org.apache.cassandra.stress.marshal.AbstractType;
import org.apache.cassandra.stress.marshal.BytesType;
import org.apache.cassandra.stress.util.Pair;

public abstract class PartitionIterator implements Iterator<Row> {

    abstract boolean reset(
            double useChance,
            double rowPopulationRatio,
            int targetCount,
            boolean isWrite,
            PartitionGenerator.Order order);

    public abstract Pair<Row, Row> resetToBounds(Seed seed, int clusteringComponentDepth);

    PartitionGenerator.Order order;
    long idseed;
    Seed seed;

    final PartitionGenerator generator;
    final SeedManager seedManager;

    final Object[] partitionKey;
    final Row row;

    public static PartitionIterator get(PartitionGenerator generator, SeedManager seedManager) {
        if (!generator.clusteringComponents.isEmpty()) return new MultiRowIterator(generator, seedManager);
        else return new SingleRowIterator(generator, seedManager);
    }

    private PartitionIterator(PartitionGenerator generator, SeedManager seedManager) {
        this.generator = generator;
        this.seedManager = seedManager;
        this.partitionKey = new Object[generator.partitionKey.size()];
        this.row = new Row(
                partitionKey, new Object[generator.clusteringComponents.size() + generator.valueComponents.size()]);
    }

    void setSeed(Seed seed) {
        long idseed = 0;
        for (int i = 0; i < partitionKey.length; i++) {
            Generator generator = this.generator.partitionKey.get(i);
            generator.setSeed(seed.seed);
            Object key = generator.generate();
            partitionKey[i] = key;
            idseed = seed(key, generator.type, idseed);
        }
        this.seed = seed;
        this.idseed = idseed;
    }

    public boolean reset(Seed seed, double useChance, double rowPopulationRatio, boolean isWrite) {
        setSeed(seed);
        this.order = generator.order;
        return reset(useChance, rowPopulationRatio, 0, isWrite, PartitionIterator.this.order);
    }

    public boolean reset(Seed seed, int targetCount, double rowPopulationRatio, boolean isWrite) {
        setSeed(seed);
        this.order = generator.order;
        return reset(Double.NaN, rowPopulationRatio, targetCount, isWrite, PartitionIterator.this.order);
    }

    static final class SingleRowIterator extends PartitionIterator {
        boolean done;
        boolean isWrite;
        double rowPopulationRatio;
        final double totalValueColumns;

        private SingleRowIterator(PartitionGenerator generator, SeedManager seedManager) {
            super(generator, seedManager);

            this.totalValueColumns = generator.valueComponents.size();
        }

        @Override
        public Pair<Row, Row> resetToBounds(Seed seed, int clusteringComponentDepth) {
            assert clusteringComponentDepth == 0;
            setSeed(seed);
            reset(1d, 1d, 1, false, PartitionGenerator.Order.SORTED);
            return Pair.create(new Row(partitionKey), new Row(partitionKey));
        }

        @Override
        boolean reset(
                double useChance,
                double rowPopulationRatio,
                int targetCount,
                boolean isWrite,
                PartitionGenerator.Order order) {
            done = false;
            this.isWrite = isWrite;
            this.rowPopulationRatio = rowPopulationRatio;
            return true;
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public Row next() {
            if (done) throw new NoSuchElementException();

            double valueColumn = 0.0;
            for (int i = 0; i < row.row.length; i++) {
                if (generator.permitNulls(i) && (++valueColumn / totalValueColumns) > rowPopulationRatio) {
                    row.row[i] = null;
                } else {
                    Generator gen = generator.valueComponents.get(i);
                    gen.setSeed(idseed);
                    row.row[i] = gen.generate();
                }
            }
            done = true;
            if (isWrite) {
                seedManager.markFirstWrite(seed, true);
                seedManager.markLastWrite(seed, true);
            }
            return row;
        }
    }

    static class MultiRowIterator extends PartitionIterator {
        final long[] clusteringSeeds = new long[generator.clusteringComponents.size()];
        final Deque<Object>[] clusteringComponents = new ArrayDeque[generator.clusteringComponents.size()];

        double useChance;
        double rowPopulationRatio;
        final double totalValueColumns;
        final double[] chancemodifier = new double[generator.clusteringComponents.size()];
        final double[] rollmodifier = new double[generator.clusteringComponents.size()];

        final int[] currentRow = new int[generator.clusteringComponents.size()];
        final int[] lastRow = new int[currentRow.length];
        boolean hasNext;
        boolean isFirstWrite;
        boolean isWrite;

        final Set<Object> unique = new HashSet<>();
        final List<Object> tosort = new ArrayList<>();

        MultiRowIterator(PartitionGenerator generator, SeedManager seedManager) {
            super(generator, seedManager);
            for (int i = 0; i < clusteringComponents.length; i++) clusteringComponents[i] = new ArrayDeque<>();
            rollmodifier[0] = 1f;
            chancemodifier[0] = generator.clusteringDescendantAverages[0];
            this.totalValueColumns = generator.valueComponents.size();
        }

        @Override
        boolean reset(
                double useChance,
                double rowPopulationRatio,
                int targetCount,
                boolean isWrite,
                PartitionGenerator.Order order) {
            this.isWrite = isWrite;
            this.rowPopulationRatio = rowPopulationRatio;

            this.order = order;
            generator.clusteringComponents.getFirst().setSeed(idseed);

            int firstComponentCount = (int) generator
                    .clusteringComponents
                    .getFirst()
                    .clusteringDistribution
                    .next();
            int expectedRowCount;

            int position = seed.position();

            if (isWrite) expectedRowCount = firstComponentCount * generator.clusteringDescendantAverages[0];
            else if (position != 0) expectedRowCount = setLastRow(position - 1);
            else expectedRowCount = setNoLastRow(firstComponentCount);

            if (Double.isNaN(useChance)) useChance = Math.clamp(targetCount / (double) expectedRowCount, 0d, 1d);
            setUseChance(useChance);

            while (true) {

                for (Queue<?> q : clusteringComponents) q.clear();
                fill(0);

                if (!isWrite) {
                    if (seek(0) != State.SUCCESS) throw new IllegalStateException();
                    return true;
                }

                int count = seed.visits == 1
                        ? 1 + (int) generator.maxRowCount
                        : Math.max(1, expectedRowCount / seed.visits);
                position = seed.moveForwards(count);
                isFirstWrite = position == 0;
                setLastRow(position + count - 1);

                switch (seek(position)) {
                    case END_OF_PARTITION -> {
                        return false;
                    }
                    case SUCCESS -> {
                        return true;
                    }
                    default -> {}
                }
            }
        }

        void setUseChance(double useChance) {
            if (this.useChance < 1d) {
                Arrays.fill(rollmodifier, 1d);
                Arrays.fill(chancemodifier, 1d);
            }
            this.useChance = useChance;
        }

        @Override
        public Pair<Row, Row> resetToBounds(Seed seed, int clusteringComponentDepth) {
            setSeed(seed);
            setUseChance(1d);
            if (clusteringComponentDepth == 0) {
                reset(1d, 1d, -1, false, PartitionGenerator.Order.SORTED);
                return Pair.create(new Row(partitionKey), new Row(partitionKey));
            }

            this.order = PartitionGenerator.Order.SORTED;
            this.rowPopulationRatio = 1d;
            this.isWrite = false;
            assert clusteringComponentDepth <= clusteringComponents.length;
            for (Queue<?> q : clusteringComponents) q.clear();

            fill(0);
            Pair<int[], Object[]> bound1 = randomBound(clusteringComponentDepth);
            Pair<int[], Object[]> bound2 = randomBound(clusteringComponentDepth);
            if (compare(bound1.left(), bound2.left()) > 0) {
                Pair<int[], Object[]> tmp = bound1;
                bound1 = bound2;
                bound2 = tmp;
            }
            Arrays.fill(lastRow, 0);
            System.arraycopy(bound2.left(), 0, lastRow, 0, bound2.left().length);
            Arrays.fill(currentRow, 0);
            System.arraycopy(bound1.left(), 0, currentRow, 0, bound1.left().length);
            seekToCurrentRow();
            return Pair.create(new Row(partitionKey, bound1.right()), new Row(partitionKey, bound2.right()));
        }

        private int setNoLastRow(int firstComponentCount) {
            Arrays.fill(lastRow, Integer.MAX_VALUE);
            return firstComponentCount * generator.clusteringDescendantAverages[0];
        }

        private int setLastRow(int position) {
            if (position < 0) throw new IllegalStateException();

            decompose(position, lastRow);
            int expectedRowCount = 0;
            for (int i = 0; i < lastRow.length; i++) {
                int l = lastRow[i];
                expectedRowCount += l * generator.clusteringDescendantAverages[i];
            }
            return expectedRowCount + 1;
        }

        private int compareToLastRow(int depth) {
            int prev = 0;
            for (int i = 0; i <= depth; i++) {
                int p = currentRow[i];
                int l = lastRow[i];
                int r = clusteringComponents[i].size();
                if (prev < 0) {
                    if (r > 1) return -1;
                } else if (p > l) {
                    return 1;
                } else if (p != l) {
                    if (r != 1) return -1;
                    prev = p - l;
                }
            }
            return 0;
        }

        private void decompose(int scalar, int[] decomposed) {
            for (int i = 0; i < decomposed.length; i++) {
                int avg = generator.clusteringDescendantAverages[i];
                decomposed[i] = scalar / avg;
                scalar %= avg;
            }
            for (int i = lastRow.length - 1; i > 0; i--) {
                int avg = generator.clusteringComponentAverages[i];
                if (decomposed[i] >= avg) {
                    decomposed[i - 1] += decomposed[i] / avg;
                    decomposed[i] %= avg;
                }
            }
        }

        private static int compare(int[] l, int[] r) {
            for (int i = 0; i < l.length; i++) if (l[i] != r[i]) return Integer.compare(l[i], r[i]);
            return 0;
        }

        enum State {
            END_OF_PARTITION,
            AFTER_LIMIT,
            SUCCESS;
        }

        private State seek(int scalar) {
            if (scalar == 0) {
                this.currentRow[0] = -1;
                clusteringComponents[0].addFirst(this);
                return setHasNext(advance(0, true));
            }
            decompose(scalar, this.currentRow);
            return seekToCurrentRow();
        }

        private State seekToCurrentRow() {
            int[] position = this.currentRow;
            for (int i = 0; i < position.length; i++) {
                if (i != 0) fill(i);
                for (int c = position[i]; c > 0; c--) clusteringComponents[i].poll();

                if (clusteringComponents[i].isEmpty()) {
                    int j = i;
                    while (true) {
                        if (--j < 0) return setHasNext(false);

                        clusteringComponents[j].poll();
                        if (!clusteringComponents[j].isEmpty()) break;
                    }

                    position[j]++;
                    Arrays.fill(position, j + 1, position.length, 0);
                    while (j < i) fill(++j);
                }

                row.row[i] = clusteringComponents[i].peek();
            }

            if (compareToLastRow(currentRow.length - 1) > 0) return setHasNext(false);

            position[position.length - 1]--;
            clusteringComponents[position.length - 1].addFirst(this);
            return setHasNext(advance(position.length - 1, true));
        }

        Row advance() {
            int depth = clusteringComponents.length - 1;
            long parentSeed = clusteringSeeds[depth];
            long rowSeed = seed(
                    clusteringComponents[depth].peek(), generator.clusteringComponents.get(depth).type, parentSeed);

            Row result = row.copy();
            double valueColumn = 0.0;

            for (int i = clusteringSeeds.length; i < row.row.length; i++) {
                Generator gen = generator.valueComponents.get(i - clusteringSeeds.length);
                if (++valueColumn / totalValueColumns > rowPopulationRatio) {
                    result.row[i] = null;
                } else {
                    gen.setSeed(rowSeed);
                    result.row[i] = gen.generate();
                }
            }

            setHasNext(advance(depth, false));
            return result;
        }

        private boolean advance(int depth, boolean first) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            clusteringComponents[depth].poll();
            currentRow[depth]++;
            while (true) {
                if (clusteringComponents[depth].isEmpty()) {
                    if (depth == 0) return false;
                    depth--;
                    clusteringComponents[depth].poll();
                    if (++currentRow[depth] > lastRow[depth]) return false;
                    continue;
                }

                int compareToLastRow = compareToLastRow(depth);
                if (compareToLastRow > 0) {
                    assert !first;
                    return false;
                }
                boolean forceReturnOne = first && compareToLastRow == 0;

                double thischance = useChance * chancemodifier[depth];
                if (forceReturnOne || thischance > 0.99999f || thischance >= random.nextDouble()) {
                    row.row[depth] = clusteringComponents[depth].peek();
                    depth++;
                    if (depth == clusteringComponents.length) return true;
                    if (useChance < 1d) {
                        rollmodifier[depth] = rollmodifier[depth - 1] / Math.min(1d, thischance);
                        chancemodifier[depth] = generator.clusteringDescendantAverages[depth] * rollmodifier[depth];
                    }
                    currentRow[depth] = 0;
                    fill(depth);
                    continue;
                }

                if (compareToLastRow >= 0) return false;

                clusteringComponents[depth].poll();
                currentRow[depth]++;
            }
        }

        private static Object elementAt(Deque<Object> deque, int index) {
            Iterator<Object> iterator = deque.iterator();
            for (int i = 0; i < index; i++) iterator.next();
            return iterator.next();
        }

        private Pair<int[], Object[]> randomBound(int clusteringComponentDepth) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int[] position = new int[clusteringComponentDepth];
            Object[] bound = new Object[clusteringComponentDepth];
            position[0] = rnd.nextInt(clusteringComponents[0].size());
            bound[0] = elementAt(clusteringComponents[0], position[0]);
            for (int d = 1; d < clusteringComponentDepth; d++) {
                fill(d);
                position[d] = rnd.nextInt(clusteringComponents[d].size());
                bound[d] = elementAt(clusteringComponents[d], position[d]);
            }
            for (int d = 1; d < clusteringComponentDepth; d++) clusteringComponents[d].clear();
            return Pair.create(position, bound);
        }

        void fill(int depth) {
            long seed = depth == 0 ? idseed : clusteringSeeds[depth - 1];
            Generator gen = generator.clusteringComponents.get(depth);
            gen.setSeed(seed);
            Object firstGenerated = fill(clusteringComponents[depth], (int) gen.clusteringDistribution.next(), gen);
            Object seedElement = order != generator.order && generator.order == PartitionGenerator.Order.ARBITRARY
                    ? firstGenerated
                    : clusteringComponents[depth].peek();
            clusteringSeeds[depth] = seed(seedElement, generator.clusteringComponents.get(depth).type, seed);
        }

        Object fill(Queue<Object> queue, int count, Generator generator) {
            if (count == 1) {
                Object only = generator.generate();
                queue.add(only);
                return only;
            }

            return switch (order) {
                case SORTED ->
                    Comparable.class.isAssignableFrom(generator.clazz)
                            ? fillSorted(queue, count, generator)
                            : fillUnique(queue, count, generator);
                case ARBITRARY -> fillUnique(queue, count, generator);
                case SHUFFLED -> fillShuffled(queue, count, generator);
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object fillSorted(Queue<Object> queue, int count, Generator generator) {
            tosort.clear();
            for (int i = 0; i < count; i++) tosort.add(generator.generate());
            Object first = tosort.getFirst();
            Collections.sort((List<Comparable>) (List<?>) tosort);
            for (int i = 0; i < count; i++)
                if (i == 0 || ((Comparable) tosort.get(i - 1)).compareTo(tosort.get(i)) < 0) queue.add(tosort.get(i));
            return first;
        }

        private Object fillUnique(Queue<Object> queue, int count, Generator generator) {
            unique.clear();
            Object first = null;
            for (int i = 0; i < count; i++) {
                Object next = generator.generate();
                if (i == 0) first = next;
                if (unique.add(next)) queue.add(next);
            }
            return first;
        }

        private Object fillShuffled(Queue<Object> queue, int count, Generator generator) {
            unique.clear();
            tosort.clear();
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                Object next = generator.generate();
                if (unique.add(next)) tosort.add(next);
            }
            Object first = tosort.getFirst();
            for (int i = 0; i < tosort.size(); i++) {
                int index = rand.nextInt(i, tosort.size());
                Object obj = tosort.get(index);
                tosort.set(index, tosort.get(i));
                queue.add(obj);
            }
            return first;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Row next() {
            if (!hasNext()) throw new NoSuchElementException();
            return advance();
        }

        public boolean finishedPartition() {
            return clusteringComponents[0].isEmpty();
        }

        private State setHasNext(boolean hasNext) {
            this.hasNext = hasNext;
            if (!hasNext) {
                boolean isLast = finishedPartition();
                if (isWrite) {
                    boolean isFirst = isFirstWrite;
                    if (isFirst) seedManager.markFirstWrite(seed, isLast);
                    if (isLast) seedManager.markLastWrite(seed, isFirst);
                }
                return isLast ? State.END_OF_PARTITION : State.AFTER_LIMIT;
            }
            return State.SUCCESS;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    static long seed(Object object, AbstractType type, long seed) {
        if (object instanceof ByteBuffer buf) {
            for (int i = buf.position(); i < buf.limit(); i++) seed = (31 * seed) + buf.get(i);
            return seed;
        } else if (object instanceof String str) {
            for (int i = 0; i < str.length(); i++) seed = (31 * seed) + str.charAt(i);
            return seed;
        } else if (object instanceof Number number) {
            return (seed * 31) + number.longValue();
        } else if (object instanceof UUID uuid) {
            return seed * 31 + (uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits());
        } else {
            return seed(type.decompose(object), BytesType.instance, seed);
        }
    }

    public Object getPartitionKey(int i) {
        return partitionKey[i];
    }

    public String getKeyAsString() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Object key : partitionKey) {
            if (i > 0) sb.append('|');
            AbstractType type = generator.partitionKey.get(i++).type;
            String typeStr = type.getString(type.decompose(key));
            if (type instanceof BytesType) {
                String decoded = tryDecodeHexAsAscii(typeStr);
                if (decoded != null)
                    sb.append(decoded).append(" (hex: ").append(typeStr).append(')');
                else sb.append(typeStr);
            } else {
                sb.append(typeStr);
            }
        }
        return sb.toString();
    }

    private static String tryDecodeHexAsAscii(String hex) {
        if (hex == null || hex.length() == 0 || hex.length() % 2 != 0) return null;
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            bytes[i] = (byte) ((hi << 4) | lo);
            if (bytes[i] < 0x20 || bytes[i] > 0x7E) return null;
        }
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
