package com.cloudsaas.tv3.worker;

public final class QueueMessageContractException extends RuntimeException {
    private final String errorCode;

    QueueMessageContractException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
