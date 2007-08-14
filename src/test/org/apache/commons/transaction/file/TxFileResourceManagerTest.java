/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.transaction.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;
import static junit.framework.Assert.fail;

import org.apache.commons.transaction.file.FileResourceManager.FileResource;
import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.locking.RWLockManager;
import org.junit.Test;

public class TxFileResourceManagerTest {
    
    private static final String ENCODING = "ISO-8859-15";

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TxFileResourceManagerTest.class);
    }

    private static final void createFiles(String[] filePaths) {
        createFiles(filePaths, null, null);
    }

    private static final void createFiles(String[] filePaths, String dirPath) {
        createFiles(filePaths, null, dirPath);
    }

    private static final void createFiles(String[] filePaths, String[] contents) {
        createFiles(filePaths, contents, null);
    }

    private static final void createFiles(String[] filePaths, String[] contents, String dirPath) {
        for (int i = 0; i < filePaths.length; i++) {
            String filePath = filePaths[i];
            File file;
            if (dirPath != null) {
                file = new File(new File(dirPath), filePath);
            } else {
                file = new File(filePath);
            }
            file.getParentFile().mkdirs();
            try {
                file.delete();
                file.createNewFile();
                String content = null;
                if (contents != null && contents.length > i) {
                    content = contents[i];
                }
                if (content != null) {
                    FileOutputStream stream = new FileOutputStream(file);
                    stream.write(contents[i].getBytes(ENCODING));
                    stream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private static final void checkIsEmpty(String dirPath) {
        checkExactlyContains(dirPath, null);
    }
    private static final void checkExactlyContains(String dirPath, String[] fileNames) {
        checkExactlyContains(dirPath, fileNames, null);
    }

    private static final void checkExactlyContains(String dirPath, String[] fileNames,
            String[] contents) {
        File dir = new File(dirPath);

        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (fileNames == null) {
                if (files.length != 0) {
                    fail(dirPath + " must be empty");
                } else {
                    return;
                }
            }

            if (files.length != fileNames.length) {
                fail(dirPath + " contains " + files.length + " instead of " + fileNames.length
                        + " files");
            }

            for (int i = 0; i < fileNames.length; i++) {
                String fileName = fileNames[i];
                boolean match = false;
                File file = null;
                for (int j = 0; j < files.length; j++) {
                    file = files[j];
                    if (file.getName().equals(fileName)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    fail(dirPath + " does not contain required " + fileName);
                }

                String content = null;
                if (contents != null && i < contents.length) {
                    content = contents[i];
                }
                if (content != null && !compare(file, content)) {
                    fail("Contents of " + fileName + " in " + dirPath
                            + " does not contain required content '" + content + "'");
                }
            }

        } else {
            fail(dirPath + " is not directoy");
        }
    }

    private static boolean compare(FileInputStream stream, byte[] bytes) {
        int read;
        int count = 0;
        try {
            while ((read = stream.read()) != -1) {
                if (bytes[count++] != read) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static boolean compare(File file, String content) {
        FileInputStream stream = null;
        try {
            byte[] bytes = content.getBytes(ENCODING);
            stream = new FileInputStream(file);
            return compare(stream, bytes);
        } catch (Throwable t) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }


    
    @Test
    public void basic() {
        TxFileResourceManager manager = new TxFileResourceManager("TxFileManager", "d:/tmp/content");
        LockManager lm = new RWLockManager<String, String>();
        FileResourceUndoManager um;
        try {
            um = new MemoryUndoManager("d:/tmp/txlogs");
            manager.setLm(lm);
            manager.setUndoManager(um);
            manager.startTransaction(60, TimeUnit.SECONDS);
            FileResource file = manager.getResource("d:/tmp/content/aha");
            if (!file.exists()) {
                file.createAsFile();
            }
            OutputStream os = file.writeStream(true);
            PrintStream ps = new PrintStream(os);
            ps.println("Huhu");
            manager.commitTransaction();
        } catch (Throwable throwable) {
            System.err.println(throwable);
            manager.rollbackTransaction();
        }

    }

    public static void main(String[] args) {
        new TxFileResourceManagerTest().basic();
    }
}
