package com.scylladb.utils;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Utility code to do optimized byte-array comparison.
 * Updated to use modern Java 25+ APIs.
 */
public class FastByteOperations
{
    public static int compareUnsigned(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2)
    {
        return Arrays.compareUnsigned(b1, s1, s1 + l1, b2, s2, s2 + l2);
    }

    public static int compareUnsigned(ByteBuffer b1, byte[] b2, int s2, int l2)
    {
        MemorySegment ms1 = MemorySegment.ofBuffer(b1).asSlice(b1.position(), b1.remaining());
        MemorySegment ms2 = MemorySegment.ofArray(b2).asSlice(s2, l2);
        return compare(ms1, ms2);
    }

    public static int compareUnsigned(byte[] b1, int s1, int l1, ByteBuffer b2)
    {
        return -compareUnsigned(b2, b1, s1, l1);
    }

    public static int compareUnsigned(ByteBuffer b1, ByteBuffer b2)
    {
        MemorySegment ms1 = MemorySegment.ofBuffer(b1).asSlice(b1.position(), b1.remaining());
        MemorySegment ms2 = MemorySegment.ofBuffer(b2).asSlice(b2.position(), b2.remaining());
        return compare(ms1, ms2);
    }

    private static int compare(MemorySegment s1, MemorySegment s2)
    {
        long mismatch = s1.mismatch(s2);
        if (mismatch == -1)
            return Long.compare(s1.byteSize(), s2.byteSize());
        
        // If mismatch is equal to one of the sizes, it means one is a prefix of the other.
        // However, mismatch returns -1 if they are equal and one is a prefix of the other? 
        // No, mismatch returns the length of the shorter segment if it's a prefix of the longer one.
        // Wait, let's check mismatch documentation.
        // "returns the offset of the first mismatch, or -1 if no mismatch"
        // If one is prefix of another, mismatch returns the size of the smaller one.
        
        if (mismatch == s1.byteSize()) return -1;
        if (mismatch == s2.byteSize()) return 1;

        return Byte.compareUnsigned(s1.get(ValueLayout.JAVA_BYTE, mismatch), s2.get(ValueLayout.JAVA_BYTE, mismatch));
    }

    public static void copy(ByteBuffer src, int srcPosition, byte[] trg, int trgPosition, int length)
    {
        if (length <= 0) return;
        MemorySegment.copy(MemorySegment.ofBuffer(src), ValueLayout.JAVA_BYTE, (long) srcPosition,
                MemorySegment.ofArray(trg), ValueLayout.JAVA_BYTE, (long) trgPosition, length);
    }

    public static void copy(ByteBuffer src, int srcPosition, ByteBuffer trg, int trgPosition, int length)
    {
        if (length <= 0) return;
        MemorySegment.copy(MemorySegment.ofBuffer(src), ValueLayout.JAVA_BYTE, (long) srcPosition,
                MemorySegment.ofBuffer(trg), ValueLayout.JAVA_BYTE, (long) trgPosition, length);
    }

    public interface ByteOperations
    {
        int compare(byte[] buffer1, int offset1, int length1,
                    byte[] buffer2, int offset2, int length2);

        int compare(ByteBuffer buffer1, byte[] buffer2, int offset2, int length2);

        int compare(ByteBuffer buffer1, ByteBuffer buffer2);

        void copy(ByteBuffer src, int srcPosition, byte[] trg, int trgPosition, int length);

        void copy(ByteBuffer src, int srcPosition, ByteBuffer trg, int trgPosition, int length);
    }
}