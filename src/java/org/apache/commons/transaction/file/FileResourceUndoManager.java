package org.apache.commons.transaction.file;

import java.io.File;

public interface FileResourceUndoManager {

    enum Code {
        DELETED_DIRECTORY, UPDATED_CONTENT, CREATED
    }
    
    void startRecord();

    void undoRecord();

    void forgetRecord();

    void recordCreate(File file);

    void recordDelete(File file);

    void recordUpdate(File file);

}
