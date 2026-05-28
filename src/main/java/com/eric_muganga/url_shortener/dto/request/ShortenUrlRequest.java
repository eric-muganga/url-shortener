package com.eric_muganga.url_shortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for shortening a URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenUrlRequest {

    @NotBlank(message = "URL is required")
    @Pattern(
            regexp = "^https?://.*",
            message = "URL must start with http:// or https://"
    )
    private String url;
}
