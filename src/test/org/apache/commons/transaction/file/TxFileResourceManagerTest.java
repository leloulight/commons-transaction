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

import static junit.framework.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;
import static junit.framework.Assert.*;
import org.junit.Test;

import org.apache.commons.transaction.file.FileResourceManager.FileResource;
import org.apache.commons.transaction.util.FileHelper;
import org.apache.commons.transaction.util.RendezvousBarrier;

public class TxFileResourceManagerTest {

    private static final String ENCODING = "ISO-8859-15";

    // XXX INCREASE THIS WHEN DEBUGGING OTHERWISE THE BARRIER WILL TIME OUT
    // AFTER TWO SECONDS
    // MOST LIKELY CONFUSING YOU COMPLETELY
    private static final long BARRIER_TIMEOUT = 200000;

    private static final String rootPath = "d:/tmp/content";

    private static final String tmpDir = "d:/tmp/txlogs";

    private static String msg;

    private static void reset() {
        removeRec(rootPath);
        removeRec(tmpDir);
    }

    private static final String[] INITIAL_FILES = new String[] { rootPath + "/olli/Hubert6",
            rootPath + "/olli/Hubert" };

    private static void removeRec(String dirPath) {
        FileHelper.removeRecursive(new File(dirPath));
    }

    private static void createInitialFiles() {
        createFiles(INITIAL_FILES);
    }

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TxFileResourceManagerTest.class);
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    // XXX need this, as JUnit seems to print only part of these strings
    private static void report(String should, String is) {
        if (!is.equals(should)) {
            fail("\nWrong output:\n'" + is + "'\nShould be:\n'" + should + "'\n");
        }
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
        TxFileResourceManager manager = new TxFileResourceManager("TxFileManager", rootPath);
        FileResourceUndoManager um;
        try {
            um = new MemoryUndoManager(tmpDir);
            manager.setUndoManager(um);
            manager.startTransaction(60, TimeUnit.SECONDS);
            FileResource file = manager.getResource(rootPath + "/aha");
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

    @Test
    public void global() throws Throwable {
        reset();
        createInitialFiles();

        final TxFileResourceManager rm = new TxFileResourceManager("TxFileManager", rootPath);
        FileResourceUndoManager um;
        um = new MemoryUndoManager(tmpDir);
        rm.setUndoManager(um);

        final RendezvousBarrier shutdownBarrier = new RendezvousBarrier("Shutdown", 3,
                BARRIER_TIMEOUT);
        final RendezvousBarrier start2Barrier = new RendezvousBarrier("Start2", BARRIER_TIMEOUT);
        final RendezvousBarrier commit1Barrier = new RendezvousBarrier("Commit1", BARRIER_TIMEOUT);

        Thread create = new Thread(new Runnable() {
            public void run() {
                try {
                    rm.startTransaction(60, TimeUnit.SECONDS);

                    shutdownBarrier.call();
                    start2Barrier.call();

                    rm.getResource(rootPath + "/olli/Hubert4").createAsFile();
                    rm.getResource(rootPath + "/olli/Hubert5").createAsFile();
                    msg = "Greetings from " + Thread.currentThread().getName() + "\n";
                    OutputStream out = rm.getResource(rootPath + "/olli/Hubert6")
                            .writeStream(false);
                    out.write(msg.getBytes(ENCODING));

                    commit1Barrier.meet();

                    rm.commitTransaction();

                    checkExactlyContains(rootPath + "/olli", new String[] { "Hubert", "Hubert4",
                            "Hubert5", "Hubert6" }, new String[] { "", "", "", msg });

                } catch (Throwable e) {
                    System.err.println("Error: " + e);
                    e.printStackTrace();
                }
            }
        }, "Create Thread");

        Thread modify = new Thread(new Runnable() {
            public void run() {
                Object txId = null;
                try {

                    {
                        InputStream in = rm.getResource(rootPath + "/olli/Hubert6").readStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in,
                                ENCODING));
                        String line = reader.readLine();
                        assertEquals(line, null);
                        in.close();
                    }

                    txId = "Modify";
                    rm.startTransaction(60, TimeUnit.SECONDS);

                    {
                        InputStream in = rm.getResource(rootPath + "/olli/Hubert6").readStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in,
                                ENCODING));
                        String line = reader.readLine();
                        assertEquals(line, null);
                        in.close();
                    }

                    shutdownBarrier.call();

                    rm.getResource(rootPath + "/olli/Hubert1").createAsFile();
                    rm.getResource(rootPath + "/olli/Hubert2").createAsFile();
                    rm.getResource(rootPath + "/olli/Hubert3").createAsFile();

                    // wait until tx commits, so there already are Hubert4 and
                    // Hubert5 and
                    // Hubert6 changes
                    commit1Barrier.meet();

                    rm.getResource(rootPath + "/olli/Hubert4").createAsFile();
                    rm.getResource(rootPath + "/olli/Hubert5").createAsFile();
                    rm.getResource(rootPath + "/olli/Hubert6").createAsFile();
                    InputStream in = rm.getResource(rootPath + "/olli/Hubert6").readStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, ENCODING));
                    String line = reader.readLine();
                    // allow for update while in tx as this is READ_COMMITED
                    report(msg, line);
                    in.close();

                    
                    rm.getResource(rootPath + "/olli/Hubert").delete();
                    rm.getResource(rootPath + "/olli/Hubert2").delete();
                    rm.getResource(rootPath + "/olli/Hubert3").delete();
                    rm.getResource(rootPath + "/olli/Hubert4").delete();
                    rm.getResource(rootPath + "/olli/Hubert5").delete();
                    
                    rm.commitTransaction();
                } catch (Throwable e) {
                    System.err.println("Error: " + e);
                    e.printStackTrace();
                }
            }
        }, "Modify Thread");

        create.start();
        // be sure first thread is started before trying next
        start2Barrier.meet();
        modify.start();

        // let both transaction start before trying to shut down
        shutdownBarrier.meet();

        checkExactlyContains(rootPath + "/olli", new String[] { "Hubert1", "Hubert6" },
                new String[] { "", msg });
    }

}
