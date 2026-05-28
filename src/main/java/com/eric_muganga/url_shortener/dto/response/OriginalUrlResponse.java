package com.eric_muganga.url_shortener.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for retrieving the original URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OriginalUrlResponse {

    private String shortCode;
    private String originalUrl;
}
