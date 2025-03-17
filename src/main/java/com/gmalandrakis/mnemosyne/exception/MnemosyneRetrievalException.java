package com.gmalandrakis.mnemosyne.exception;

public class MnemosyneRetrievalException extends MnemosyneRuntimeException{
    public MnemosyneRetrievalException(Exception e) {
        super(e);
    }

    public MnemosyneRetrievalException(String message) {
        super(message);
    }
}
