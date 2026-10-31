package com.smarthire.common;

/** 409 - the request conflicts with existing state (duplicate, illegal transition, ...). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
