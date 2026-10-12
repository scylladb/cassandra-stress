// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class DynamicList<E> {
    public static final class Node<E> {
        private final int[] size;

        private final Node<E>[] links;
        private E value;

        private Node(int height, E value) {
            this.value = value;
            links = new Node[height * 2];
            size = new int[height];
            Arrays.fill(size, 1);
        }

        private int height() {
            return size.length;
        }

        private Node<E> next(int i) {
            return links[i * 2];
        }

        private Node<E> prev(int i) {
            return links[1 + i * 2];
        }

        private void setNext(int i, Node<E> next) {
            links[i * 2] = next;
        }

        private void setPrev(int i, Node<E> prev) {
            links[1 + i * 2] = prev;
        }
    }

    private final int maxHeight;
    private final Node<E> head;
    private int size;

    public DynamicList(int maxExpectedSize) {
        this.maxHeight = 3 + Math.max(0, (int) Math.ceil(Math.log(maxExpectedSize) / Math.log(2)));
        head = new Node<>(maxHeight, null);
    }

    private int randomLevel() {
        return 1 + Integer.bitCount(ThreadLocalRandom.current().nextInt() & ((1 << (maxHeight - 1)) - 1));
    }

    public Node<E> append(E value) {
        return append(value, Integer.MAX_VALUE);
    }

    public Node<E> append(E value, int maxSize) {
        Node<E> newTail = new Node<>(randomLevel(), value);
        if (size >= maxSize) {
            return null;
        }
        size++;

        Node<E> tail = head;
        for (int i = maxHeight - 1; i >= newTail.height(); i--) {
            Node<E> next;
            while ((next = tail.next(i)) != null) {
                tail = next;
            }
            tail.size[i]++;
        }

        for (int i = newTail.height() - 1; i >= 0; i--) {
            Node<E> next;
            while ((next = tail.next(i)) != null) {
                tail = next;
            }
            tail.setNext(i, newTail);
            newTail.setPrev(i, tail);
        }

        return newTail;
    }

    public void remove(Node<E> node) {
        assert node.value != null;
        node.value = null;

        size--;

        for (int i = 0; i < node.height(); i++) {
            Node<E> prev = node.prev(i);
            Node<E> next = node.next(i);
            assert prev != null;
            prev.setNext(i, next);
            if (next != null) {
                next.setPrev(i, prev);
            }
            prev.size[i] += node.size[i] - 1;
        }

        for (int i = node.height(); i < maxHeight; i++) {
            while (i == node.height()) {
                node = node.prev(i - 1);
            }
            node.size[i]--;
        }
    }

    public E get(int index) {
        if (index >= size) {
            return null;
        }

        index++;
        int c = 0;
        Node<E> finger = head;
        for (int i = maxHeight - 1; i >= 0; i--) {
            while (c + finger.size[i] <= index) {
                c += finger.size[i];
                finger = finger.next(i);
            }
        }

        assert c == index;
        return finger.value;
    }

    public int size() {
        return size;
    }
}
