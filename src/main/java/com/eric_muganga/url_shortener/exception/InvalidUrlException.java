package com.eric_muganga.url_shortener.exception;

import lombok.Getter;

/**
 * Exception thrown when a URL fails validation.
 *
 * This can occur when:
 * - The URL is not a valid URL format (malformed)
 * - The URL uses an unsupported protocol (e.g., ftp:// instead of http(s)://)
 * - The URL exceeds maximum allowed length
 *
 * Typically results in a 400 Bad Request HTTP response.
 */
@Getter
public class InvalidUrlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * -- GETTER --
     *  Get the invalid URL.
     */
    private final String url;
    /**
     * -- GETTER --
     *  Get the reason why the URL is invalid.
     */
    private final String reason;

    /**
     * Construct with a message.
     *
     * @param message Description of the validation error
     */
    public InvalidUrlException(String message) {
        super(message);
        this.url = null;
        this.reason = message;
    }

    /**
     * Construct with a URL and reason.
     *
     * @param url The invalid URL
     * @param reason Why the URL is invalid
     */
    public InvalidUrlException(String url, String reason) {
        super(String.format("Invalid URL: %s. Reason: %s", url, reason));
        this.url = url;
        this.reason = reason;
    }

    /**
     * Construct with a message and underlying cause.
     *
     * @param message Description of the validation error
     * @param cause The underlying exception (e.g., MalformedURLException)
     */
    public InvalidUrlException(String message, Throwable cause) {
        super(message, cause);
        this.url = null;
        this.reason = message;
    }

}
