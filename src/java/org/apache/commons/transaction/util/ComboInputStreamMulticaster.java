package org.apache.commons.transaction.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// TODO: Add two dedicated locks for close/open resp. spawn
public class ComboInputStreamMulticaster implements InputStreamMulticaster {

    private int memoryBufferSize = 8192;

    private Log log = LogFactory.getLog(getClass());

    protected List<InputStream> spawned;

    protected byte buf[];

    protected File bufferFile;

    protected boolean isOpen = false;

    @Override
    public synchronized void close() {
        if (!isOpen) {
            throw new IllegalStateException("You can not close: Stream multicaster is not open!");
        }
        isOpen = false;
    }

    @Override
    public synchronized void open(InputStream backingInputStream) throws IOException {
        if (isOpen) {
            throw new IllegalStateException(
                    "You can not open a new stream: Stream multicaster is already open!");
        }
        if (backingInputStream == null) {
            throw new IllegalStateException("You can not open a null stream!");
        }

        buf = new byte[memoryBufferSize];
        spawned = new ArrayList<InputStream>();

        try {
            int len = backingInputStream.read(buf);
            // which means the memory buffer hasn't been large enough
            if (len == buf.length) {
                // if so, we buffer in a file
                this.bufferFile = File.createTempFile("muticast", null);
                OutputStream os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(bufferFile));
                    os.write(buf);
                    int read;
                    while ((read = backingInputStream.read(buf)) != -1) {
                        os.write(buf, 0, read);
                    }
                } finally {
                    buf = null;
                    if (os != null)
                        os.close();
                }
            }
        } finally {
            backingInputStream.close();
        }
        isOpen = true;
    }

    @Override
    public synchronized InputStream spawn() throws IOException {
        if (!isOpen) {
            throw new IllegalStateException(
                    "You can not spwan new streams: Stream multicaster has already been closed!");
        }
        InputStream sis = null;
        if (buf != null) {
            sis = new ByteArrayInputStream(buf) {
                @Override
                public void close() throws IOException {
                    closeSpawned(this);
                    super.close();
                }
            };
        } else {
            try {
                sis = new BufferedInputStream(new FileInputStream(bufferFile)) {
                    @Override
                    public void close() throws IOException {
                        closeSpawned(this);
                        super.close();
                    }
                };
            } catch (FileNotFoundException e) {
                // fatal as this really should have been created
                log.fatal("Internal error: Buffer file has not been created", e);
            }
        }
        spawned.add(sis);
        return sis;
    }

    public int getMemoryBufferSize() {
        return memoryBufferSize;
    }

    public void setMemoryBufferSize(int memoryBufferSize) {
        this.memoryBufferSize = memoryBufferSize;
    }

    public synchronized void forceShutdown() {
        isOpen = false;
        if (spawned != null) {
            for (InputStream is : spawned) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.warn("Could not close spawned input stream on forced shutdown", e);
                }
            }
        }
        cleanUp();
    }

    protected void closeSpawned(InputStream is) {
        spawned.remove(this);
        if (!isOpen && spawned.isEmpty()) {
            cleanUp();
        }
    }

    protected void cleanUp() {
        if (bufferFile != null) {
            bufferFile.delete();
            bufferFile = null;
        }
        buf = null;
        spawned = null;
    }

}
