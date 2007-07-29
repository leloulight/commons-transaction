package org.apache.commons.transaction.file;

import java.io.File;

public interface FileResourceUndoManager {

    public enum Code {
        DELETED_DIRECTORY, CONTENT_CHANGED, CREATED_DIRECTORY, CREATED_FILE
    }
    
    public void startRecord();

    public void undoRecord();

    public void forgetRecord();

    public void recordCreateAsDirectory(File directory);
    public void recordCreateAsFile(File file);

    public void recordDelete(File file);

    public void recordChangeContent(File file);

}
