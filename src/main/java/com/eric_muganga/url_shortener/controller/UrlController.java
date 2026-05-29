package com.eric_muganga.url_shortener.controller;

import com.eric_muganga.url_shortener.dto.request.ShortenUrlRequest;
import com.eric_muganga.url_shortener.dto.request.UpdateUrlRequest;
import com.eric_muganga.url_shortener.dto.response.OriginalUrlResponse;
import com.eric_muganga.url_shortener.dto.response.ShortenUrlResponse;
import com.eric_muganga.url_shortener.dto.response.StatsResponse;
import com.eric_muganga.url_shortener.dto.response.UpdateUrlResponse;
import com.eric_muganga.url_shortener.exception.InvalidUrlException;
import com.eric_muganga.url_shortener.exception.UrlNotFoundException;
import com.eric_muganga.url_shortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * REST API for URL shortening operations.
 *
 * Endpoints:
 * POST   /api/v1/urls/shorten           - Create short URL
 * GET    /api/v1/urls/{shortCode}       - Get original URL
 * GET    /api/v1/urls/{shortCode}/r     - Redirect to original URL
 * PUT    /api/v1/urls/{shortCode}       - Update short URL mapping
 * DELETE /api/v1/urls/{shortCode}       - Delete short URL
 * GET    /api/v1/urls/{shortCode}/stats - Get access statistics
 */
@RestController
@RequestMapping("/api/v1/urls")
@Validated
@Slf4j
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;


    /**
     * POST /api/v1/urls/shorten
     * Create a new short URL from a long URL.
     *
     * @param request containing the original URL
     * @return ShortenUrlResponse with short code and shortened URL
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) {
        try {
            String shortCode = urlService.shortenUrl(request.getUrl());
            log.info("Short URL created: {}", shortCode);

            ShortenUrlResponse response = ShortenUrlResponse.builder()
                    .shortCode(shortCode)
                    .shortenedUrl("http://localhost:8080/api/v1/urls/" + shortCode + "/r")
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (InvalidUrlException e) {
            log.warn("Invalid URL provided: {}", request.getUrl());
            throw e; // Let @ExceptionHandler deal with it
        } catch (Exception e) {
            log.error("Error creating short URL", e);
            throw e;
        }
    }

    /**
     * GET /api/v1/urls/{shortCode}
     * Retrieve the original URL for a given short code.
     *
     * @param shortCode the short code
     * @return OriginalUrlResponse with original URL
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<OriginalUrlResponse> getOriginalUrl(@PathVariable String shortCode) {
        try {
            String originalUrl = urlService.getOriginalUrl(shortCode);
            log.debug("Retrieved URL for short code: {}", shortCode);

            OriginalUrlResponse response = OriginalUrlResponse.builder()
                    .shortCode(shortCode)
                    .originalUrl(originalUrl)
                    .build();

            return ResponseEntity.ok(response);

        } catch (UrlNotFoundException e) {
            log.warn("Short code not found: {}", shortCode);
            throw e;
        } catch (Exception e) {
            log.warn("Error retrieving URL for short code: {}", shortCode, e);
            throw e;
        }
    }

    /**
     * GET /api/v1/urls/{shortCode}/r
     * Redirect to the original URL.
     *
     * @param shortCode the short code
     * @return HTTP 302 redirect to original URL
     */
    @GetMapping("/{shortCode}/r")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        try {
            String originalUrl = urlService.getOriginalUrl(shortCode);
            log.info("Redirecting {} to original URL", shortCode);

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, originalUrl)
                    .build();

        } catch (UrlNotFoundException e) {
            log.warn("Short code not found during redirect: {}", shortCode);
            throw e;
        } catch (Exception e) {
            log.warn("Error redirecting short code: {}", shortCode, e);
            throw e;
        }
    }

    /**
     * PUT /api/v1/urls/{shortCode}
     * Update the original URL mapped to a short code.
     *
     * @param shortCode the short code
     * @param request containing the new URL
     * @return UpdateUrlResponse with confirmation
     */
    @PutMapping("/{shortCode}")
    public ResponseEntity<UpdateUrlResponse> updateUrl(
            @PathVariable String shortCode,
            @Valid @RequestBody UpdateUrlRequest request) {

        try {
            urlService.updateUrl(shortCode, request.getUrl());
            log.info("URL updated: {} -> {}", shortCode, request.getUrl());

            UpdateUrlResponse response = UpdateUrlResponse.builder()
                    .message("URL updated successfully")
                    .shortCode(shortCode)
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(response);

        } catch (InvalidUrlException e) {
            log.warn("Invalid new URL provided for short code: {}", shortCode);
            throw e;
        } catch (UrlNotFoundException e) {
            log.warn("Short code not found: {}", shortCode);
            throw e;
        } catch (Exception e) {
            log.warn("Error updating URL for short code: {}", shortCode, e);
            throw e;
        }
    }

    /**
     * DELETE /api/v1/urls/{shortCode}
     * Delete (soft delete) a short URL.
     *
     * @param shortCode the short code
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        try {
            urlService.deleteUrl(shortCode);
            log.info("URL deleted: {}", shortCode);

            return ResponseEntity.noContent().build();

        } catch (UrlNotFoundException e) {
            log.warn("Short code not found: {}", shortCode);
            throw e;
        } catch (Exception e) {
            log.warn("Error deleting URL for short code: {}", shortCode, e);
            throw e;
        }
    }

    /**
     * GET /api/v1/urls/{shortCode}/stats
     * Get access statistics for a short URL.
     *
     * @param shortCode the short code
     * @return StatsResponse with URL metadata and stats
     */
    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<StatsResponse> getStats(@PathVariable String shortCode) {
        try {
            StatsResponse stats = urlService.getStats(shortCode);
            log.debug("Retrieved stats for short code: {}", shortCode);

            return ResponseEntity.ok(stats);

        } catch (UrlNotFoundException e) {
            log.warn("Short code not found: {}", shortCode);
            throw e;
        } catch (Exception e) {
            log.warn("Error retrieving stats for short code: {}", shortCode, e);
            throw e;
        }
    }
}
