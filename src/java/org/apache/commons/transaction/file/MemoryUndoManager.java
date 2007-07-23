package org.apache.commons.transaction.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.util.FileHelper;

// TODO: memory version to be serialized to XML using JAXB
public class MemoryUndoManager implements FileResourceUndoManager {

    private Log logger = LogFactory.getLog(getClass());
    protected ThreadLocal<List<UndoRecord>> localRecords = new ThreadLocal<List<UndoRecord>>();

    
    public void recordChangeContent(File file) {
        UndoRecord record = new UndoRecord();
        record.code = Code.CONTENT_CHANGED;
        record.file = file;
        try {
            record.oldConent = new ByteArrayInputStream(FileHelper.readInto(file));
        } catch (IOException e) {
            logger.fatal("Could not store changed content for "+file);
            // FIXME: This really should cause an error
        }
        storeRecord(record);
    }

    public void recordCopy(File from, File to) {
        if (to.exists()) {
            recordChangeContent(to);
        }
        UndoRecord record = new UndoRecord();
        record.code = Code.COPIED;
        record.file = from;
        record.to = to;
        storeRecord(record);
    }

    public void recordCreateAsDirectory(File directory) {
        UndoRecord record = new UndoRecord();
        record.code = Code.CREATED_DIRECTORY;
        record.file = directory;
        storeRecord(record);
    }

    public void recordCreateAsFile(File file) {
        UndoRecord record = new UndoRecord();
        record.code = Code.CREATED_FILE;
        record.file = file;
        storeRecord(record);
    }

    public void recordDelete(File file) {
        if (file.isFile()) {
            recordChangeContent(file);
        } else {
            UndoRecord record = new UndoRecord();
            record.code = Code.DELETED_DIRECTORY;
            record.file = file;
            storeRecord(record);
        }
    }

    public void recordMove(File from, File to) {
        if (to.exists()) {
            recordChangeContent(to);
        }
        UndoRecord record = new UndoRecord();
        record.code = Code.MOVED;
        record.file = from;
        record.to = to;
        storeRecord(record);
    }

    public void recordChangeProperty(File file, String name, Object oldValue) {
        UndoRecord record = new UndoRecord();
        record.code = Code.PROPERTY_CHANGED;
        record.file = file;
        record.propertyName = name;
        record.oldValue = oldValue;
        storeRecord(record);
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
        localRecords.set(null);
    }

    protected void storeRecord(UndoRecord record) {
        List<UndoRecord> records = localRecords.get();
        records.add(record);
    }

    protected static class UndoRecord {
        Code code;

        File file;

        File to;

        String propertyName;

        Object oldValue;

        InputStream oldConent;

        // FIXME: Needs implementation (not that hard)
        // ugly c-style - who cares?
        public void undo() {
            // TODO
            switch (code) {
            
            }
            
        }
    }

}
