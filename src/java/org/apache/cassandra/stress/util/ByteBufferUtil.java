// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.util;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public final class ByteBufferUtil
{
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[0]);

    private ByteBufferUtil()
    {
    }

    public static int compareUnsigned(ByteBuffer o1, ByteBuffer o2)
    {
        int index = o1.mismatch(o2);
        if (index < 0)
            return 0;
        if (index < o1.remaining() && index < o2.remaining())
            return (o1.get(o1.position() + index) & 0xFF) - (o2.get(o2.position() + index) & 0xFF);
        return o1.remaining() - o2.remaining();
    }

    public static String string(ByteBuffer buffer) throws CharacterCodingException
    {
        return string(buffer, StandardCharsets.UTF_8);
    }

    public static String string(ByteBuffer buffer, Charset charset) throws CharacterCodingException
    {
        return charset.newDecoder().decode(buffer.duplicate()).toString();
    }

    public static byte[] getArray(ByteBuffer buffer)
    {
        int length = buffer.remaining();
        if (buffer.hasArray())
        {
            int offset = buffer.arrayOffset() + buffer.position();
            return Arrays.copyOfRange(buffer.array(), offset, offset + length);
        }
        byte[] bytes = new byte[length];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    public static ByteBuffer bytes(String s)
    {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    public static ByteBuffer bytes(String s, Charset charset)
    {
        return ByteBuffer.wrap(s.getBytes(charset));
    }

    public static ByteBuffer bytes(byte b)
    {
        return ByteBuffer.allocate(1).put(0, b);
    }

    public static ByteBuffer bytes(short s)
    {
        return ByteBuffer.allocate(2).putShort(0, s);
    }

    public static ByteBuffer bytes(int i)
    {
        return ByteBuffer.allocate(4).putInt(0, i);
    }

    public static ByteBuffer bytes(long n)
    {
        return ByteBuffer.allocate(8).putLong(0, n);
    }

    public static ByteBuffer bytes(float f)
    {
        return ByteBuffer.allocate(4).putFloat(0, f);
    }

    public static ByteBuffer bytes(double d)
    {
        return ByteBuffer.allocate(8).putDouble(0, d);
    }

    public static ByteBuffer bytes(InetAddress address)
    {
        return ByteBuffer.wrap(address.getAddress());
    }

    public static ByteBuffer bytes(UUID uuid)
    {
        return UUIDGen.toByteBuffer(uuid);
    }

    public static int toInt(ByteBuffer bytes)
    {
        return bytes.getInt(bytes.position());
    }

    public static short toShort(ByteBuffer bytes)
    {
        return bytes.getShort(bytes.position());
    }

    public static long toLong(ByteBuffer bytes)
    {
        return bytes.getLong(bytes.position());
    }

    public static float toFloat(ByteBuffer bytes)
    {
        return bytes.getFloat(bytes.position());
    }

    public static double toDouble(ByteBuffer bytes)
    {
        return bytes.getDouble(bytes.position());
    }

    public static String bytesToHex(ByteBuffer bytes)
    {
        return HexFormat.of().formatHex(getArray(bytes));
    }

    public static ByteBuffer hexToBytes(String hex)
    {
        return ByteBuffer.wrap(HexFormat.of().parseHex(hex));
    }

    public static ByteBuffer readBytes(ByteBuffer bb, int length)
    {
        ByteBuffer copy = bb.duplicate();
        copy.limit(copy.position() + length);
        bb.position(bb.position() + length);
        return copy;
    }
}
