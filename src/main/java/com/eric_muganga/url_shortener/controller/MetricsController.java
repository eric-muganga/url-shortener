package com.eric_muganga.url_shortener.controller;

import com.eric_muganga.url_shortener.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
@Slf4j
@RequiredArgsConstructor
public class MetricsController {

    private final CacheMetrics metrics;

    @GetMapping("/snapshot")
    public ResponseEntity<CacheMetrics.CacheMetricsSnapshot> getMetricsSnapshot() {
        CacheMetrics.CacheMetricsSnapshot snapshot = metrics.getMetricsSnapshot();
        log.debug("Metrics snapshot requested. Hit rate: {}", snapshot.getCacheHitRatioPercent());

        return ResponseEntity.ok(snapshot);
    }

}
