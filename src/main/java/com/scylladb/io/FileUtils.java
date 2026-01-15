package com.scylladb.io;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class FileUtils {
    private static final File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private static final AtomicLong tempFileNum = new AtomicLong();

    /**
     * Pretty much like {@link File#createTempFile(String, String, File)}, but with
     * the guarantee that the "random" part of the generated file name between
     * {@code prefix} and {@code suffix} is a positive, increasing {@code long} value.
     */
    public static File createTempFile(String prefix, String suffix, File directory) throws FSException {
        try {
            // Do not use java.io.File.createTempFile(), because some tests rely on the
            // behavior that the "random" part in the temp file name is a positive 'long'.
            // However, at least since Java 9 the code to generate the "random" part
            // uses an _unsigned_ random long generated like this:
            // Long.toUnsignedString(new java.util.Random.nextLong())

            while (true) {
                // The contract of File.createTempFile() says, that it must not return
                // the same file name again. We do that here in a very simple way,
                // that probably doesn't cover all edge cases. Just rely on system
                // wall clock and return strictly increasing values from that.
                long num = tempFileNum.getAndIncrement();

                // We have a positive long here, which is safe to use for example
                // for CommitLogTest.
                String fileName = prefix + Long.toString(num) + suffix;
                File candidate = new File(directory, fileName);
                if (candidate.createNewFile())
                    return candidate;
            }
        } catch (IOException e) {
            throw new FSException(e, directory.getAbsolutePath());
        }
    }

    public static File createTempFile(String prefix, String suffix) {
        return createTempFile(prefix, suffix, tempDir);
    }
}
