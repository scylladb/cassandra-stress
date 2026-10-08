// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockedDynamicList<E> extends DynamicList<E> {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LockedDynamicList(int maxExpectedSize) {
        super(maxExpectedSize);
    }

    @Override
    public Node<E> append(E value, int maxSize) {
        lock.writeLock().lock();
        try {
            return super.append(value, maxSize);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(Node<E> node) {
        lock.writeLock().lock();
        try {
            super.remove(node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public E get(int index) {
        lock.readLock().lock();
        try {
            return super.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return super.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
