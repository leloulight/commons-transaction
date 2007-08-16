package org.apache.commons.transaction.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.util.FileHelper;

/**
 * Default implementation of {@link FileResourceUndoManager}.
 * 
 * <p>Undo information is held in memory only. Changed content streams are stored as temporary files.
 * 
 * <p><em>Caution:</em>This implementation does not guarantee ACID properties after a JVM crash.
 *
 * <p>
 * This implementation is <em>thread-safe</em>.
 */
public class MemoryUndoManager implements FileResourceUndoManager {

    private Log logger = LogFactory.getLog(getClass());

    protected ThreadLocal<List<UndoRecord>> localRecords = new ThreadLocal<List<UndoRecord>>();

    private final File logDirectory;

    public MemoryUndoManager(String logDir) throws IOException {
        logDirectory = new File(logDir);
        logDirectory.mkdirs();
    }

    public void startRecord() {
        localRecords.set(new ArrayList<UndoRecord>());
    }

    public void undoRecord() {
        List<UndoRecord> records = new ArrayList<UndoRecord>(localRecords.get());
        Collections.reverse(records);
        for (UndoRecord record : records) {
            record.undo();
        }
    }

    public void forgetRecord() {
        List<UndoRecord> records = new ArrayList<UndoRecord>(localRecords.get());
        for (UndoRecord record : records) {
            record.cleanUp();
        }
        localRecords.set(null);
    }

    protected void storeRecord(UndoRecord record) {
        List<UndoRecord> records = localRecords.get();
        records.add(record);
    }

    public void recordUpdate(File file) {
        recordUpdate(file, false);
    }

    protected void recordUpdate(File file, boolean moveAllowed) {
        try {
            new UndoRecord(Code.UPDATED_CONTENT, file, FileHelper.makeBackup(file, logDirectory,
                    moveAllowed));
        } catch (IOException e) {
            // FIXME: This really is fatal: How to signal?
            logger.fatal("Can not record content update", e);
        }
    }

    public void recordCreate(File file) {
        new UndoRecord(Code.CREATED, file);
    }

    public void recordDelete(File file) {
        if (file.isFile()) {
            recordUpdate(file, true);
        } else {
            new UndoRecord(Code.DELETED_DIRECTORY, file);
        }
    }

    protected class UndoRecord {
        Code code;

        File file;

        File updatedFile;

        public UndoRecord(Code code, File file) {
            this(code, file, null);
        }

        public UndoRecord(Code code, File file, File updatedFile) {
            this.code = code;
            this.file = file;
            this.updatedFile = updatedFile;
            save();
        }

        protected void save() {
            storeRecord(this);
        }

        public void cleanUp() {
            if (updatedFile != null)
                updatedFile.delete();
        }

        public void undo() {
            switch (code) {
            case DELETED_DIRECTORY:
                file.mkdirs();
                break;
            case CREATED:
                file.delete();
                break;
            case UPDATED_CONTENT:
                try {
                    FileHelper.move(updatedFile, file);
                } catch (IOException e) {
                    // FIXME: This really is fatal: How to signal?
                    logger.fatal("Can not undo content update", e);
                }
                break;
            }

        }
    }

}
