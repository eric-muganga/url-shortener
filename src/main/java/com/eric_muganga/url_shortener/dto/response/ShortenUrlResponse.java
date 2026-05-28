package com.eric_muganga.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for shortened URL creation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenUrlResponse {

    private String shortCode;
    private String shortenedUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
