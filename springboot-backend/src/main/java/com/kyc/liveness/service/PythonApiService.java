package com.kyc.liveness.service;

import com.kyc.liveness.dto.PythonCheckResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the Python FastAPI /check_result endpoint for face-match verification.
 */
@Service
public class PythonApiService {

    private static final Logger log = LoggerFactory.getLogger(PythonApiService.class);

    private final RestTemplate restTemplate;
    private final RestTemplate pythonPostRestTemplate;

    @Value("${python.api.base-url:http://localhost:8000}")
    private String pythonApiBaseUrl;

    public PythonApiService(
            RestTemplate restTemplate,
            @Qualifier("pythonPostRestTemplate") RestTemplate pythonPostRestTemplate) {
        this.restTemplate = restTemplate;
        this.pythonPostRestTemplate = pythonPostRestTemplate;
    }

    public PythonCheckResultResponse fetchCheckResult(String sessionId) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(pythonApiBaseUrl)
                    .path("/check_result")
                    .queryParam("session_id", sessionId)
                    .toUriString();

            ResponseEntity<PythonCheckResultResponse> response =
                    restTemplate.getForEntity(url, PythonCheckResultResponse.class);

            if (response.getBody() != null) {
                log.info("Python /check_result GET — session: {} | face_score: {} | final_result: {}",
                        sessionId, response.getBody().getFaceScore(), response.getBody().getFinalResult());
                return response.getBody();
            }
        } catch (RestClientException ex) {
            log.warn("Python GET /check_result failed for session {}: {}", sessionId, ex.getMessage());
        }
        return null;
    }

    public PythonCheckResultResponse postCheckResult(String sessionId, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        try {
            String url = pythonApiBaseUrl + "/check_result";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "liveness-selfie.jpg";
                }
            });
            body.add("session_id", sessionId);
            body.add("attempt_no", "1");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<PythonCheckResultResponse> response =
                    pythonPostRestTemplate.postForEntity(url, request, PythonCheckResultResponse.class);

            if (response.getBody() != null) {
                log.info("Python /check_result POST — session: {} | face_score: {} | final_result: {}",
                        sessionId, response.getBody().getFaceScore(), response.getBody().getFinalResult());
                return response.getBody();
            }
        } catch (RestClientException ex) {
            log.warn("Python POST /check_result failed for session {}: {}", sessionId, ex.getMessage());
        }
        return null;
    }
}
