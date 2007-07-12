package org.apache.commons.transaction.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.JUnit4TestAdapter;

import org.apache.commons.transaction.util.ComboInputStreamMulticaster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ComboInputStreamMulticasterTest {
    ComboInputStreamMulticaster ism1;

    ComboInputStreamMulticaster ism2;

    ComboInputStreamMulticaster ism3;

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(ComboInputStreamMulticasterTest.class);
    }

    static InputStream fakeStream(int length) {
        return new ByteArrayInputStream(init(new byte[length])) {
            boolean isClosed = false;

            @Override
            public void close() throws IOException {
                if (isClosed)
                    throw new IOException("Already closed!");
                isClosed = true;
            }
        };
    }

    static InputStream fakeStream() {
        return fakeStream(1);
    }

    static byte[] init(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    @Before
    public void createMulticasters() {
        ism1 = new ComboInputStreamMulticaster();
        ism2 = new ComboInputStreamMulticaster();
        ism3 = new ComboInputStreamMulticaster();
    }

    @After
    public void destroyMulticasters() {
        ism1.forceShutdown();
        ism1 = null;
        ism2.forceShutdown();
        ism2 = null;
        ism3.forceShutdown();
        ism3 = null;
    }

    @Test(expected = IllegalStateException.class)
    public void closeWithoutOpen() throws IOException {
        ism1.close();
    }

    @Test(expected = IllegalStateException.class)
    public void openWhileOpen() throws IOException {
        ism1.open(fakeStream());
        ism1.open(fakeStream());
    }

    @Test(expected = IOException.class)
    public void backinStreamMemClosed() throws IOException {
        InputStream backingStream = fakeStream(ism1.getMemoryBufferSize() - 1);
        ism1.open(backingStream);
        backingStream.close();
    }

    @Test(expected = IOException.class)
    public void backinStreamFileClosed() throws IOException {
        InputStream backingStream = fakeStream(ism1.getMemoryBufferSize() + 1);
        ism1.open(backingStream);
        backingStream.close();
    }

    @Test(expected = IllegalStateException.class)
    public void spwanWithoutOpen() throws IOException {
        ism1.spawn();
    }

    @Test
    public void bufferMemory() throws IOException {
        ism1.open(fakeStream(1000));

        InputStream is11 = ism1.spawn();
        InputStream is12 = ism1.spawn();
        InputStream is13 = ism1.spawn();

        is11.close();
        ism1.close();

    }

    @Test
    public void bufferFile() throws IOException {
        ism1.open(fakeStream(1000));

        InputStream is11 = ism1.spawn();
        InputStream is12 = ism1.spawn();
        InputStream is13 = ism1.spawn();

        is11.close();
        ism1.close();

    }

}
