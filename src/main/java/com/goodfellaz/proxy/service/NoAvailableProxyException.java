package com.goodfellaz.proxy.service;

public class NoAvailableProxyException extends RuntimeException {
    public NoAvailableProxyException(String message) {
        super(message);
    }
}
