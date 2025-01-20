package com.gmalandrakis.mnemosyne.exception;

public class MnemosyneRuntimeException extends RuntimeException{

    public MnemosyneRuntimeException(Exception e){
        super(e);
    }
    public MnemosyneRuntimeException(String message){
        super(message);
    }
}
