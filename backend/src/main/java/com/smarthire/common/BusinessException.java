package com.smarthire.common;

/** 400-level business rule violation. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
