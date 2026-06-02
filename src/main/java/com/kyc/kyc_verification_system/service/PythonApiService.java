package com.kyc.kyc_verification_system.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;

import reactor.core.publisher.Mono;

@Service
public class PythonApiService {

    private static final Logger log = LoggerFactory.getLogger(PythonApiService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient pythonGetWebClient;
    private final WebClient pythonPostWebClient;

    @Value("${python.api.poll-interval-ms:2500}")
    private long pollIntervalMs;

    @Value("${python.api.poll-max-wait-ms:300000}")
    private long pollMaxWaitMs;

    public PythonApiService(
            @Qualifier("pythonGetWebClient") WebClient pythonGetWebClient,
            @Qualifier("pythonPostWebClient") WebClient pythonPostWebClient) {
        this.pythonGetWebClient = pythonGetWebClient;
        this.pythonPostWebClient = pythonPostWebClient;
    }

    /**
     * Polls Python GET /check_result until a terminal result or max wait (default 5 minutes).
     */
    public PythonCheckResultResponse pollUntilResolved(String sessionId) {
        long deadline = System.currentTimeMillis() + pollMaxWaitMs;
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            PythonCheckResultResponse current = fetchCheckResult(sessionId);
            if (isTerminal(current)) {
                log.info(
                        "Python poll succeeded for session {} on attempt {} — {}",
                        sessionId,
                        attempt,
                        current.getFinalResult());
                return current;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }

            long sleepMs = Math.min(pollIntervalMs, remaining);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Python poll interrupted for session {}", sessionId);
                break;
            }
        }

        log.warn("Python poll timed out for session {} after {} ms", sessionId, pollMaxWaitMs);
        return null;
    }

    public PythonCheckResultResponse fetchCheckResult(String sessionId) {
        try {
            Map<String, Object> raw = pythonGetWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/check_result")
                            .queryParam("session_id", sessionId)
                            .build())
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block();

            PythonCheckResultResponse body = mapResponse(raw);
            if (body != null) {
                log.debug(
                        "Python GET /check_result — session: {} | final_result: {}",
                        sessionId,
                        body.getFinalResult());
            }
            return body;
        } catch (WebClientResponseException ex) {
            log.warn(
                    "Python GET /check_result HTTP {} for session {}: {}",
                    ex.getStatusCode().value(),
                    sessionId,
                    ex.getMessage());
        } catch (Exception ex) {
            log.warn("Python GET /check_result failed for session {}: {}", sessionId, ex.getMessage());
        }
        return null;
    }

    public PythonCheckResultResponse postCheckResult(String sessionId, byte[] imageBytes, int attemptNo) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        try {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder
                    .part("file", new ByteArrayResource(imageBytes) {
                        @Override
                        public String getFilename() {
                            return "liveness-selfie.jpg";
                        }
                    })
                    .contentType(MediaType.IMAGE_JPEG);
            bodyBuilder.part("session_id", sessionId);
            bodyBuilder.part("attempt_no", String.valueOf(attemptNo));

            Map<String, Object> raw = pythonPostWebClient
                    .post()
                    .uri("/check_result")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block();

            PythonCheckResultResponse mapped = mapResponse(raw);
            if (mapped != null) {
                log.info(
                        "Python POST /check_result — session: {} | face_score: {} | final_result: {}",
                        sessionId,
                        mapped.getFaceScore(),
                        mapped.getFinalResult());
            }
            return mapped;
        } catch (WebClientResponseException ex) {
            log.warn(
                    "Python POST /check_result HTTP {} for session {}: {}",
                    ex.getStatusCode().value(),
                    sessionId,
                    ex.getMessage());
        } catch (Exception ex) {
            log.warn("Python POST /check_result failed for session {}: {}", sessionId, ex.getMessage());
        }
        return null;
    }

    /** Fire-and-forget POST so polling can read the cached result from Python. */
    public Mono<Void> postCheckResultAsync(String sessionId, byte[] imageBytes, int attemptNo) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Mono.empty();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder
                .part("file", new ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return "liveness-selfie.jpg";
                    }
                })
                .contentType(MediaType.IMAGE_JPEG);
        bodyBuilder.part("session_id", sessionId);
        bodyBuilder.part("attempt_no", String.valueOf(attemptNo));

        return pythonPostWebClient
                .post()
                .uri("/check_result")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .doOnNext(raw -> {
                    PythonCheckResultResponse mapped = mapResponse(raw);
                    if (mapped != null) {
                        log.info(
                                "Python async POST done — session: {} | final_result: {}",
                                sessionId,
                                mapped.getFinalResult());
                    }
                })
                .doOnError(ex ->
                        log.warn("Python async POST failed for session {}: {}", sessionId, ex.getMessage()))
                .then();
    }

    public Duration getPollMaxWait() {
        return Duration.ofMillis(pollMaxWaitMs);
    }

    static boolean isTerminal(PythonCheckResultResponse result) {
        if (result == null || result.getFinalResult() == null) {
            return false;
        }
        String status = result.getFinalResult();
        return !"PENDING".equalsIgnoreCase(status)
                && !"PROCESSING".equalsIgnoreCase(status);
    }

    private PythonCheckResultResponse mapResponse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        PythonCheckResultResponse mapped = new PythonCheckResultResponse();
        mapped.setSessionId(stringVal(raw.get("session_id")));
        mapped.setAttemptNo(intVal(raw.get("attempt_no")));
        mapped.setConfidence(doubleVal(raw.get("confidence")));
        mapped.setVerified(boolVal(raw.get("verified")));
        mapped.setFaceScore(stringVal(raw.get("face_score")));
        mapped.setFinalResult(stringVal(raw.get("final_result")));

        if (mapped.getFinalResult() == null) {
            log.warn("Python response missing final_result: {}", raw);
            return null;
        }

        return mapped;
    }

    private String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double doubleVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean boolVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
