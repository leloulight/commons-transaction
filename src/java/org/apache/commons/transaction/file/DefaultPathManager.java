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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.util.FileHelper;

public class DefaultPathManager implements PathManager {

    private Log logger = LogFactory.getLog(getClass());

    protected String workChangeDir = "change";

    protected String workDeleteDir = "delete";

    protected String workDir;

    protected String storeDir;

    public String getStoreDir() {
        return storeDir;
    }

    public void setStoreDir(String storeDir) {
        this.storeDir = storeDir;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getWorkChangeDir() {
        return workChangeDir;
    }

    public void setWorkChangeDir(String workChangeDir) {
        this.workChangeDir = workChangeDir;
    }

    public String getWorkDeleteDir() {
        return workDeleteDir;
    }

    public void setWorkDeleteDir(String workDeleteDir) {
        this.workDeleteDir = workDeleteDir;
    }

    public String getPathForId(String resourceId) {
        String path = resourceId.toString();
        try {
            path = URLEncoder.encode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // we know this will not happen
        }
        return path;
    }

    public String assureLeadingSlash(String pathObject) {
        String path = "";
        if (pathObject != null) {
            path = getPathForId(pathObject);
            if (path.length() > 0 && path.charAt(0) != '/' && path.charAt(0) != '\\') {
                path = "/" + path;
            }
        }
        return path;
    }

    public String getMainPath(String path) {
        StringBuffer buf = new StringBuffer(storeDir.length() + path.toString().length() + 5);
        buf.append(storeDir).append(assureLeadingSlash(path));
        return buf.toString();
    }

    public String getCurrentTxId() {
        return Thread.currentThread().toString();
    }

    public String getTransactionBaseDir() {
        return workDir + '/' + getCurrentTxId();
    }

    public String getChangePath(String path) {
        String txBaseDir = getTransactionBaseDir();
        StringBuffer buf = new StringBuffer(txBaseDir.length() + path.toString().length()
                + getWorkChangeDir().length() + 5);
        buf.append(txBaseDir).append('/').append(getWorkChangeDir()).append(
                assureLeadingSlash(path));
        return buf.toString();
    }

    public String getPathForDelete(String path) {
        String txBaseDir = getTransactionBaseDir();
        StringBuffer buf = new StringBuffer(txBaseDir.length() + path.toString().length()
                + getWorkDeleteDir() + 5);
        buf.append(txBaseDir).append('/').append(getWorkDeleteDir()).append(
                assureLeadingSlash(path));
        return buf.toString();
    }

    public String getPathForWrite(String path) {
        try {
            // when we want to write, be sure to write to a local copy
            String txChangePath = getChangePath(path);
            if (!FileHelper.fileExists(txChangePath)) {
                FileHelper.createFile(txChangePath);
            }
            return txChangePath;
        } catch (IOException e) {
            throw new Error("Can not write to resource at '" + path);

        }
    }

    public String getPathForRead(String resourceId) {

        String mainPath = getMainPath(resourceId);
        String txChangePath = getChangePath(resourceId);
        String txDeletePath = getPathForDelete(resourceId);

        // now, this gets a bit complicated:

        boolean changeExists = FileHelper.fileExists(txChangePath);
        boolean deleteExists = FileHelper.fileExists(txDeletePath);
        boolean mainExists = FileHelper.fileExists(mainPath);
        boolean resourceIsDir = ((mainExists && new File(mainPath).isDirectory()) || (changeExists && new File(
                txChangePath).isDirectory()));
        if (resourceIsDir) {
            logger.warn("Resource at '" + resourceId + "' maps to directory");
        }

        // first do some sane checks

        // this may never be, two cases are possible, both disallowing to have a
        // delete together with a change
        // 1. first there was a change, than a delete -> at least delete file
        // exists (when there is a file in main store)
        // 2. first there was a delete, than a change -> only change file exists
        if (!resourceIsDir && changeExists && deleteExists) {
            throw new Error("Inconsistent delete and change combination for resource at '"
                    + resourceId + "'");
        }

        // you should not have been allowed to delete a file that does not exist
        // at all
        if (deleteExists && !mainExists) {
            throw new Error("Inconsistent delete for resource at '" + resourceId + "'");
        }

        if (changeExists) {
            return txChangePath;
        } else if (mainExists && !deleteExists) {
            return mainPath;
        } else {
            return null;
        }
    }

    public String getChangeBaseDir() {
        return getTransactionBaseDir() + getWorkChangeDir();
    }

    public String getDeleteBaseDir() {
        return getTransactionBaseDir() + getWorkDeleteDir();
    }

}