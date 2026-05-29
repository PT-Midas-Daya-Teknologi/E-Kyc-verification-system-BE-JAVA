package com.kyc.liveness.exception;

public class LivenessException extends RuntimeException {

    public LivenessException(String message) {
        super(message);
    }

    public LivenessException(String message, Throwable cause) {
        super(message, cause);
    }
}
