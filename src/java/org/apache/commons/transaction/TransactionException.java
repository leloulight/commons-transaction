package org.apache.commons.transaction;



public class TransactionException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = 7650329971392401844L;

    public enum Code {
        COMMIT_FAILED,
        ROLLBACK_ONLY
    }

    protected Code code;

    public TransactionException(Throwable cause, Code code) {
        super(cause);
        this.code = code;
    }

    public TransactionException(Code code) {
        this.code = code;
    }
    
    /**
     * Returns the formal reason for the exception.
     * 
     * @return the reason code
     */
    public Code getCode() {
        return code;
    }


}
