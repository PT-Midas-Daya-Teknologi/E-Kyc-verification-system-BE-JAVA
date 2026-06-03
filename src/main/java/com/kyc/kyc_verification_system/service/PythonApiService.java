package com.kyc.kyc_verification_system.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;

@Service
public class PythonApiService {

    private static final Logger log = LoggerFactory.getLogger(PythonApiService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient pythonPostWebClient;

    public PythonApiService(
            @Qualifier("pythonPostWebClient") WebClient pythonPostWebClient) {
        this.pythonPostWebClient = pythonPostWebClient;
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
