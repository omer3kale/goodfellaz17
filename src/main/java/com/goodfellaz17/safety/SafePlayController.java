package com.goodfellaz17.safety;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

/**
 * Safe play endpoint: all plays must go through validator first.
 */
@RestController
@RequestMapping("/api/play")
public class SafePlayController {

    @Autowired
    private SpotifySafetyValidator validator;

    @PostMapping("/safe")
    public ResponseEntity<?> playSafe(
            @RequestParam String trackId,
            @RequestParam String accountId,
            @RequestParam String ipAddress,
            @RequestParam String country,
            @RequestParam(defaultValue = "180") int durationSeconds) {

        SpotifySafetyValidator.PlayAttemptRequest request =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, durationSeconds);

        SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);

        if (!result.approved) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                result.reason,
                result.violations
            ));
        }

        validator.recordPlay(request);

        return ResponseEntity.ok(new PlayResponse(
            "success",
            "Play recorded safely",
            validator.getTotalPlaysForThesis()
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        return ResponseEntity.ok(new MetricsResponse(
            validator.getTotalPlaysForThesis(),
            "All guardrails active"
        ));
    }

    public static class ErrorResponse {
        public String violation;
        public java.util.List<String> details;

        public ErrorResponse(String violation, java.util.List<String> details) {
            this.violation = violation;
            this.details = details;
        }
    }

    public static class PlayResponse {
        public String status;
        public String message;
        public long totalPlaysThesis;

        public PlayResponse(String status, String message, long totalPlaysThesis) {
            this.status = status;
            this.message = message;
            this.totalPlaysThesis = totalPlaysThesis;
        }
    }

    public static class MetricsResponse {
        public long totalPlaysThesis;
        public String safetyStatus;

        public MetricsResponse(long totalPlays, String status) {
            this.totalPlaysThesis = totalPlays;
            this.safetyStatus = status;
        }
    }
}
