package com.kyc.kyc_verification_system.controller;



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

import com.kyc.kyc_verification_system.dto.CredentialsResponse;
import com.kyc.kyc_verification_system.dto.LivenessResultResponse;
import com.kyc.kyc_verification_system.dto.LivenessUploadResponse;
import com.kyc.kyc_verification_system.dto.SessionResponse;
import com.kyc.kyc_verification_system.dto.DocUploadResponse;
import com.kyc.kyc_verification_system.service.LivenessService;


@RestController
@RequestMapping("/api/liveness")
public class LivenessController {

    private static final Logger log = LoggerFactory.getLogger(LivenessController.class);

    private final LivenessService livenessService;

    public LivenessController(LivenessService livenessService) {
        this.livenessService = livenessService;
    }

    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("KYC Liveness Backend is running.");
    }

    
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

    
    @PostMapping("/create-session")
    public ResponseEntity<SessionResponse> createSession() {
        log.info("Request: POST /create-session");
        return ResponseEntity.ok(livenessService.createSession());
    }

   
    @GetMapping("/result/{sessionId}")
    public ResponseEntity<LivenessResultResponse> getResult(@PathVariable String sessionId) {
        log.info("Request: GET /result/{}", sessionId);
        return ResponseEntity.ok(livenessService.getResult(sessionId));
    }

    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LivenessUploadResponse> uploadVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "timestamp", required = false) String timestamp) {

        log.info("Request: POST /upload — session: {}, size: {} bytes", sessionId, video.getSize());
        return ResponseEntity.ok(livenessService.saveVideo(video, sessionId, timestamp));
    }
}
