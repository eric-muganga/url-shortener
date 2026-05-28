package com.eric_muganga.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for URL update operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUrlResponse {

    private String message;
    private String shortCode;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}