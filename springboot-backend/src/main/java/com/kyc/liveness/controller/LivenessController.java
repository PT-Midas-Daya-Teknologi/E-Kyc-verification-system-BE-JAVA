package com.kyc.liveness.controller;

import com.kyc.liveness.dto.CredentialsResponse;
import com.kyc.liveness.dto.LivenessResultResponse;
import com.kyc.liveness.dto.SessionResponse;
import com.kyc.liveness.dto.UploadResponse;
import com.kyc.liveness.service.LivenessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for KYC Face Liveness Verification.
 *
 * Endpoints:
 *   GET  /api/liveness/health                → Health check
 *   GET  /api/liveness/credentials           → Temporary AWS credentials for frontend
 *   POST /api/liveness/create-session        → Create Rekognition liveness session
 *   GET  /api/liveness/result/{sessionId}    → Fetch liveness result
 *   POST /api/liveness/upload                → Upload verification video
 */
@RestController
@RequestMapping("/api/liveness")
public class LivenessController {

    private static final Logger log = LoggerFactory.getLogger(LivenessController.class);

    private final LivenessService livenessService;

    public LivenessController(LivenessService livenessService) {
        this.livenessService = livenessService;
    }

    /**
     * GET /api/liveness/health
     * Simple health check — confirms the backend is running.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("KYC Liveness Backend is running.");
    }

    /**
     * GET /api/liveness/credentials
     * Returns short-lived AWS credentials (via STS GetSessionToken) for the
     * React FaceLivenessDetector credentialProvider.
     * Eliminates the need for Cognito / Identity Pool on the frontend.
     *
     * Response: { accessKeyId, secretAccessKey, sessionToken, region }
     */
    @GetMapping("/credentials")
    public ResponseEntity<CredentialsResponse> getCredentials() {
        log.info("Request: GET /credentials");
        CredentialsResponse creds = livenessService.getTemporaryCredentials();
        log.info("Response: Returning credentials with region={}, hasAccessKeyId={}, hasSecretAccessKey={}, hasSessionToken={}", 
            creds.getRegion(),
            creds.getAccessKeyId() != null && !creds.getAccessKeyId().isEmpty(),
            creds.getSecretAccessKey() != null && !creds.getSecretAccessKey().isEmpty(),
            creds.getSessionToken() != null && !creds.getSessionToken().isEmpty());
        return ResponseEntity.ok(creds);
    }

    /**
     * POST /api/liveness/create-session
     * Creates a new AWS Rekognition Face Liveness session.
     * Called by the React frontend before launching FaceLivenessDetector.
     *
     * Response: { sessionId, message }
     */
    @PostMapping("/create-session")
    public ResponseEntity<SessionResponse> createSession() {
        log.info("Request: POST /create-session");
        return ResponseEntity.ok(livenessService.createSession());
    }

    /**
     * GET /api/liveness/result/{sessionId}
     * Fetches the liveness verification result from AWS Rekognition.
     * Called by the React frontend after FaceLivenessDetector completes.
     *
     * Response: { sessionId, status, confidence, isLive, message }
     */
    @GetMapping("/result/{sessionId}")
    public ResponseEntity<LivenessResultResponse> getResult(@PathVariable String sessionId) {
        log.info("Request: GET /result/{}", sessionId);
        return ResponseEntity.ok(livenessService.getResult(sessionId));
    }

    /**
     * POST /api/liveness/upload
     * Receives the recorded WebM verification video from the React frontend.
     * Saves it to the local uploads/ directory.
     *
     * Form-data params: video (file), sessionId (string), timestamp (string, optional)
     * Response: { success, filename, sessionId, timestamp, size, message }
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "timestamp", required = false) String timestamp) {

        log.info("Request: POST /upload — session: {}, size: {} bytes", sessionId, video.getSize());
        return ResponseEntity.ok(livenessService.saveVideo(video, sessionId, timestamp));
    }
}
