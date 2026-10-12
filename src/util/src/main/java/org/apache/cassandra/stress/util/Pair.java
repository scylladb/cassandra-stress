package org.apache.cassandra.stress.util;

public record Pair<L, R>(L left, R right) {
    public static <L, R> Pair<L, R> create(L left, R right) {
        return new Pair<>(left, right);
    }
}
