package com.eric_muganga.url_shortener.exception;

import lombok.Getter;

/**
 * Exception thrown when a requested short URL cannot be found in the database.
 *
 * Typically results in a 404 Not Found HTTP response.
 */
@Getter
public class UrlNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * -- GETTER --
     *  Get the short code that was not found.
     */
    private final String shortCode;

    /**
     * Construct with a message and short code.
     *
     * @param message Description of the error
     * @param shortCode The short code that was not found
     */
    public UrlNotFoundException(String message, String shortCode) {
        super(message);
        this.shortCode = shortCode;
    }

    /**
     * Construct with just a message.
     *
     * @param message Description of the error
     */
    public UrlNotFoundException(String message) {
        super(message);
        this.shortCode = null;
    }

}
