package com.getsentry.raven.buffer;

import java.io.File;

public class BufferTest {
    protected void delete(File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            for (File c : dir.listFiles()) {
                delete(c);
            }
        }
        if (!dir.delete()) {
            throw new RuntimeException("Failed to delete dir: " + dir);
        }
    }
}
