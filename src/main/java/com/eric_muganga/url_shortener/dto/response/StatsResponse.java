package com.eric_muganga.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for URL access statistics.
 *
 * TODO: Implement access_logs table tracking
 * - Track each redirect access with timestamp, user agent, IP
 * - Populate totalAccesses and lastAccessedAt from access_logs
 * - See UrlService.getStats() for implementation details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsResponse {

    private String shortCode;
    private String originalUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastAccessedAt; // Null if never accessed

    private Long totalAccesses; // Currently hardcoded to 0
}
