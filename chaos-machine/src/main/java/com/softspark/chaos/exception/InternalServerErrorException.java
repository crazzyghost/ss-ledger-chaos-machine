package com.softspark.chaos.exception;

/**
 * Exception thrown for internal server errors (500).
 */
public class InternalServerErrorException extends HttpException {

    public InternalServerErrorException(String message) {
        super(500, message);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
