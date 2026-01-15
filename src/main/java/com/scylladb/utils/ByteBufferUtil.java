/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scylladb.utils;

import java.io.*;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Utility methods to make ByteBuffers less painful
 * The following should illustrate the different ways byte buffers can be used
 * <p>
 * public void testArrayOffet()
 * {
 * <p>
 * byte[] b = "test_slice_array".getBytes();
 * ByteBuffer bb = ByteBuffer.allocate(1024);
 * <p>
 * assert bb.position() == 0;
 * assert bb.limit()    == 1024;
 * assert bb.capacity() == 1024;
 * <p>
 * bb.put(b);
 * <p>
 * assert bb.position()  == b.length;
 * assert bb.remaining() == bb.limit() - bb.position();
 * <p>
 * ByteBuffer bb2 = bb.slice();
 * <p>
 * assert bb2.position()    == 0;
 * <p>
 * //slice should begin at other buffers current position
 * assert bb2.arrayOffset() == bb.position();
 * <p>
 * //to match the position in the underlying array one needs to
 * //track arrayOffset
 * assert bb2.limit()+bb2.arrayOffset() == bb.limit();
 * <p>
 * <p>
 * assert bb2.remaining() == bb.remaining();
 * <p>
 * }
 * <p>
 * }
 *
 */
public class ByteBufferUtil {
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[0]);

    /**
     * Decode a String representation.
     * This method assumes that the encoding charset is UTF_8.
     *
     * @param buffer a byte buffer holding the string representation
     * @return the decoded string
     */
    public static String string(ByteBuffer buffer) throws CharacterCodingException {
        return string(buffer, StandardCharsets.UTF_8);
    }

    /**
     * Decode a String representation.
     * This method assumes that the encoding charset is UTF_8.
     *
     * @param buffer   a byte buffer holding the string representation
     * @param position the starting position in {@code buffer} to start decoding from
     * @param length   the number of bytes from {@code buffer} to use
     * @return the decoded string
     */
    public static String string(ByteBuffer buffer, int position, int length) throws CharacterCodingException {
        return string(buffer, position, length, StandardCharsets.UTF_8);
    }

    /**
     * Decode a String representation.
     *
     * @param buffer   a byte buffer holding the string representation
     * @param position the starting position in {@code buffer} to start decoding from
     * @param length   the number of bytes from {@code buffer} to use
     * @param charset  the String encoding charset
     * @return the decoded string
     */
    public static String string(ByteBuffer buffer, int position, int length, Charset charset) throws CharacterCodingException {
        ByteBuffer copy = buffer.duplicate();
        copy.position(position);
        copy.limit(copy.position() + length);
        return string(copy, charset);
    }

    /**
     * Decode a String representation.
     *
     * @param buffer  a byte buffer holding the string representation
     * @param charset the String encoding charset
     * @return the decoded string
     */
    public static String string(ByteBuffer buffer, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().decode(buffer.duplicate()).toString();
    }

    /**
     * You should almost never use this.  Instead, use the write* methods to avoid copies.
     */
    public static byte[] getArray(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (buffer.hasArray()) {
            int boff = buffer.arrayOffset() + buffer.position();
            return Arrays.copyOfRange(buffer.array(), boff, boff + length);
        }
        // else, DirectByteBuffer.get() is the fastest route
        byte[] bytes = new byte[length];
        buffer.duplicate().get(bytes);

        return bytes;
    }

    /**
     * Encode a String in a ByteBuffer using UTF_8.
     *
     * @param s the string to encode
     * @return the encoded string
     */
    public static ByteBuffer bytes(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encode a String in a ByteBuffer using the provided charset.
     *
     * @param s       the string to encode
     * @param charset the String encoding charset to use
     * @return the encoded string
     */
    public static ByteBuffer bytes(String s, Charset charset) {
        return ByteBuffer.wrap(s.getBytes(charset));
    }

    /**
     * Transfer bytes from one ByteBuffer to another.
     * This function acts as System.arrayCopy() but for ByteBuffers.
     *
     * @param src    the source ByteBuffer
     * @param srcPos starting position in the source ByteBuffer
     * @param dst    the destination ByteBuffer
     * @param dstPos starting position in the destination ByteBuffer
     * @param length the number of bytes to copy
     */
    public static void arrayCopy(ByteBuffer src, int srcPos, ByteBuffer dst, int dstPos, int length) {
        FastByteOperations.copy(src, srcPos, dst, dstPos, length);
    }

    public static int put(ByteBuffer src, ByteBuffer trg) {
        int length = Math.min(src.remaining(), trg.remaining());
        arrayCopy(src, src.position(), trg, trg.position(), length);
        trg.position(trg.position() + length);
        src.position(src.position() + length);
        return length;
    }


    public static ByteBuffer read(DataInput in, int length) throws IOException {
        if (length == 0)
            return EMPTY_BYTE_BUFFER;

        byte[] buff = new byte[length];
        in.readFully(buff);
        return ByteBuffer.wrap(buff);
    }

    /**
     * Convert a byte buffer to an integer.
     * Does not change the byte buffer position.
     *
     * @param bytes byte buffer to convert to integer
     * @return int representation of the byte buffer
     */
    public static int toInt(ByteBuffer bytes) {
        return bytes.getInt(bytes.position());
    }

    /**
     * Convert a byte buffer to a short.
     * Does not change the byte buffer position.
     *
     * @param bytes byte buffer to convert to short
     * @return short representation of the byte buffer
     */
    public static short toShort(ByteBuffer bytes) {
        return bytes.getShort(bytes.position());
    }

    public static long toLong(ByteBuffer bytes) {
        return bytes.getLong(bytes.position());
    }

    public static float toFloat(ByteBuffer bytes) {
        return bytes.getFloat(bytes.position());
    }

    public static double toDouble(ByteBuffer bytes) {
        return bytes.getDouble(bytes.position());
    }

    public static ByteBuffer bytes(byte b) {
        return ByteBuffer.allocate(1).put(0, b);
    }

    public static ByteBuffer bytes(short s) {
        return ByteBuffer.allocate(2).putShort(0, s);
    }

    public static ByteBuffer bytes(int i) {
        return ByteBuffer.allocate(4).putInt(0, i);
    }

    public static ByteBuffer bytes(long n) {
        return ByteBuffer.allocate(8).putLong(0, n);
    }

    public static ByteBuffer bytes(float f) {
        return ByteBuffer.allocate(4).putFloat(0, f);
    }

    public static ByteBuffer bytes(double d) {
        return ByteBuffer.allocate(8).putDouble(0, d);
    }

    /*
     * Does not modify position or limit of buffer even temporarily
     * so this is safe even without duplication.
     */
    public static String bytesToHex(ByteBuffer bytes) {
        if (bytes.hasArray()) {
            return Hex.bytesToHex(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        }

        final int offset = bytes.position();
        final int size = bytes.remaining();
        final char[] c = new char[size * 2];
        for (int i = 0; i < size; i++) {
            final int bint = bytes.get(i + offset);
            c[i * 2] = Hex.byteToChar[(bint & 0xf0) >> 4];
            c[1 + i * 2] = Hex.byteToChar[bint & 0x0f];
        }
        return Hex.wrapCharArray(c);
    }

    public static ByteBuffer bytes(InetAddress address) {
        return ByteBuffer.wrap(address.getAddress());
    }

    public static ByteBuffer bytes(UUID uuid) {
        return ByteBuffer.wrap(UUIDGen.decompose(uuid));
    }

    // changes bb position
    public static ByteBuffer readBytes(ByteBuffer bb, int length) {
        ByteBuffer copy = bb.duplicate();
        copy.limit(copy.position() + length);
        bb.position(bb.position() + length);
        return copy;
    }

    /**
     * Check is the given buffer contains a given sub-buffer.
     *
     * @param buffer    The buffer to search for sequence of bytes in.
     * @param subBuffer The buffer to match.
     * @return true if buffer contains sub-buffer, false otherwise.
     */
    public static boolean contains(ByteBuffer buffer, ByteBuffer subBuffer) {
        int len = subBuffer.remaining();
        if (buffer.remaining() - len < 0)
            return false;

        // adapted form the JDK's String.indexOf()
        byte first = subBuffer.get(subBuffer.position());
        int max = buffer.position() + (buffer.remaining() - len);

        for (int i = buffer.position(); i <= max; i++) {
            /* Look for first character. */
            if (buffer.get(i) != first) {
                while (++i <= max && buffer.get(i) != first) {
                }
            }

            /* (maybe) Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + len - 1;
                for (int k = 1 + subBuffer.position(); j < end && buffer.get(j) == subBuffer.get(k); j++, k++) {
                }

                if (j == end)
                    return true;
            }
        }
        return false;
    }

}
