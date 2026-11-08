package com.example.dyslexiaapp.speech;

public class CloudTtsUnavailableException extends RuntimeException {

    public CloudTtsUnavailableException(String message) {
        super(message);
    }

    public CloudTtsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
