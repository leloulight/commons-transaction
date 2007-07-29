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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;

import org.apache.commons.transaction.file.FileResourceManager.FileResource;
import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.locking.RWLockManager;
import org.junit.Test;

public class TxFileResourceManagerTest {
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TxFileResourceManagerTest.class);
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
            file.createAsFile();
            OutputStream os = file.writeStream(false);
            PrintStream ps = new PrintStream(os);
            ps.print("Huhu");
            manager.commitTransaction();
        } catch (Throwable throwable) {
            manager.rollbackTransaction();
        }

    }

    public static void main(String[] args) {
        new TxFileResourceManagerTest().basic();
    }
}
