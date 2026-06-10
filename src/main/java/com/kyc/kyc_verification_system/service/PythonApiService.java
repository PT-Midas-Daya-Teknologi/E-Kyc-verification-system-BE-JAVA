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
            log.warn("🔴 postCheckResult — imageBytes is null or empty for session: {}", sessionId);
            return null;
        }

        try {
            log.info("🟠 postCheckResult REQUEST START ——————————————————————————");
            log.info("  📤 Sending snapshot to Python API");
            log.info("     • endpoint: /check_result");
            log.info("     • sessionId: {}", sessionId);
            log.info("     • attemptNo: {}", attemptNo);
            log.info("     • imageSize: {} bytes", imageBytes.length);

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

            log.info("  🔄 Built multipart request");
            log.info("  📤 Posting to Python API...");

            Map<String, Object> raw = pythonPostWebClient
                    .post()
                    .uri("/check_result")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block();

            log.info("  ✅ Python API response received (raw): {}", raw);

            PythonCheckResultResponse mapped = mapResponse(raw);
            if (mapped != null) {
                log.info("🟢 postCheckResult RESPONSE SUCCESS ————————————————————————");
                log.info("  sessionId: {}", mapped.getSessionId());
                log.info("  attemptNo: {}", mapped.getAttemptNo());
                log.info("  faceScore: {}", mapped.getFaceScore());
                log.info("  confidence: {}", mapped.getConfidence());
                log.info("  verified: {}", mapped.getVerified());
                log.info("  finalResult: {}", mapped.getFinalResult());
                log.info("🟢 postCheckResult REQUEST END ————————————————————————");
            } else {
                log.warn("🟡 postCheckResult — mapped response is null despite non-null raw response");
            }
            return mapped;

        } catch (WebClientResponseException ex) {
            log.error(
                    "🔴 postCheckResult FAILED — HTTP {} from Python API for session {}: {}",
                    ex.getStatusCode().value(),
                    sessionId,
                    ex.getMessage());
            log.error("    Response body: {}", ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error(
                    "🔴 postCheckResult FAILED — Exception for session {}: {}",
                    sessionId,
                    ex.getMessage(),
                    ex);
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
        
        // Get final_result from response
        String finalResult = stringVal(raw.get("final_result"));
        
        // If final_result is missing, derive it from verified status or other fields
        if (finalResult == null) {
            log.warn("Python response missing final_result, deriving from verified status");
            Boolean verified = mapped.getVerified();
            
            if (verified != null && verified) {
                finalResult = "VERIFIED";
            } else {
                finalResult = "REJECTED";
            }
            log.info("Derived final_result: {}", finalResult);
        }
        
        mapped.setFinalResult(finalResult);

        // Final validation: ensure we have a final_result
        if (mapped.getFinalResult() == null) {
            log.warn("Python response missing final_result and unable to derive: {}", raw);
            mapped.setFinalResult("REJECTED");
        }

        log.debug("Mapped response: sessionId={}, attemptNo={}, confidence={}, verified={}, faceScore={}, finalResult={}",
                mapped.getSessionId(), mapped.getAttemptNo(), mapped.getConfidence(), 
                mapped.getVerified(), mapped.getFaceScore(), mapped.getFinalResult());

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
