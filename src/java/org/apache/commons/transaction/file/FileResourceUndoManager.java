package org.apache.commons.transaction.file;

import java.io.File;

public interface FileResourceUndoManager {

    public enum Code {
        DELETED_DIRECTORY, UPDATED_CONTENT, CREATED
    }
    
    public void startRecord();

    public void undoRecord();

    public void forgetRecord();

    public void recordCreate(File file);

    public void recordDelete(File file);

    public void recordUpdate(File file);

}
