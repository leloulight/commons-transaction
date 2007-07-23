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
package org.apache.commons.transaction.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

/**
 * Helper methods for file manipulation. All methods are <em>thread safe</em>.
 * 
 * @version $Id: FileHelper.java 493628 2007-01-07 01:42:48Z joerg $
 */
public final class FileHelper {
    
    public static byte[] readInto(File file) throws IOException {
        long length = file.length();
        
        // XXX we can't do more than int
        byte[] result = new byte[(int)length];
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        is.read(result);
        return result;
    }
    
    public static void copyUsingNIO(File sourceFile, File destinationFile) throws IOException {
        // try again using NIO copy
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(sourceFile);
            fos = new FileOutputStream(destinationFile);
            FileChannel srcChannel = fis.getChannel();
            FileChannel dstChannel = fos.getChannel();
            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
            srcChannel.close();
            dstChannel.close();
        } finally {
            try {
                fis.close();
            } finally {
                fos.close();
            }
        }
    }

    public static boolean moveUsingNIO(File sourceFile, File destinationFile) throws IOException {
        // try fast file-system-level move/rename first
        boolean success = sourceFile.renameTo(destinationFile);

        if (!success) {
            copyUsingNIO(sourceFile, destinationFile);
            success = sourceFile.delete();
        }

        return success;
    }
    
    /**
     * Deletes a file specified by a path.
     *  
     * @param path path of file to be deleted
     * @return <code>true</code> if file has been deleted, <code>false</code> otherwise
     */
    public static boolean deleteFile(String path) {
        File file = new File(path);
        return file.delete();
    }

    /**
     * Checks if a file specified by a path exits.
     *  
     * @param path path of file to be checked
     * @return <code>true</code> if file exists, <code>false</code> otherwise
     */
    public static boolean fileExists(String path) {
        File file = new File(path);
        return file.exists();
    }


}
