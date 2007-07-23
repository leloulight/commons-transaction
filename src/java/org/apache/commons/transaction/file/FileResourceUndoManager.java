package org.apache.commons.transaction.file;

import java.io.File;

public interface FileResourceUndoManager {

    public enum Code {
        CREATED_DIRECTORY, CREATED_FILE, DELETED_DIRECTORY, MOVED, COPIED, CONTENT_CHANGED, PROPERTY_CHANGED
    }
    
    public void startRecord();

    public void undoRecord();

    public void forgetRecord();

    public void recordCopy(File from, File to);

    public void recordCreateAsDirectory(File directory);

    public void recordCreateAsFile(File file);

    public void recordDelete(File file);

    public void recordMove(File from, File to);

    public void recordChangeProperty(File file, String name, Object oldValue);

    public void recordChangeContent(File file);

}
