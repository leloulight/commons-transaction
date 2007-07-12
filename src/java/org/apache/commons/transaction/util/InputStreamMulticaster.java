package org.apache.commons.transaction.util;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamMulticaster {
    void open(InputStream backingInputStream) throws IOException;

    InputStream spawn() throws IOException;

    void close() throws IOException;
}
