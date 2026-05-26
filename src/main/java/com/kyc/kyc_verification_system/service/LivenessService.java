
package com.kyc.kyc_verification_system.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.kyc.kyc_verification_system.dto.CredentialsResponse;
import com.kyc.kyc_verification_system.dto.LivenessResultResponse;
import com.kyc.kyc_verification_system.dto.LivenessUploadResponse;
import com.kyc.kyc_verification_system.dto.SessionResponse;
import com.kyc.kyc_verification_system.exception.LivenessException;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequestSettings;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;


@Service
public class LivenessService {

    private static final Logger log = LoggerFactory.getLogger(LivenessService.class);

    private final RekognitionClient rekognitionClient;
    private final StsClient stsClient;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.accessKeyId}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey}")
    private String secretAccessKey;

    @Value("${liveness.confidence.threshold:90.0}")
    private double confidenceThreshold;

    @Value("${upload.directory:uploads}")
    private String uploadDirectory;

    public LivenessService(RekognitionClient rekognitionClient, StsClient stsClient) {
        this.rekognitionClient = rekognitionClient;
        this.stsClient = stsClient;
    }

   
    public CredentialsResponse getTemporaryCredentials() {
    try {
        GetSessionTokenRequest request = GetSessionTokenRequest.builder()
            .durationSeconds(3600) 
            .build();

        GetSessionTokenResponse response = stsClient.getSessionToken(request);
        var creds = response.credentials();

        log.info("Issued STS temporary credentials, expiry: {}", creds.expiration());
        
        if (creds.accessKeyId() == null || creds.accessKeyId().isEmpty()) {
            throw new RuntimeException("STS response missing accessKeyId");
        }
        if (creds.secretAccessKey() == null || creds.secretAccessKey().isEmpty()) {
            throw new RuntimeException("STS response missing secretAccessKey");
        }
        if (creds.sessionToken() == null || creds.sessionToken().isEmpty()) {
            throw new RuntimeException("STS response missing sessionToken");
        }
        
        log.debug("Credentials validated - all fields present");

        CredentialsResponse credResp = new CredentialsResponse(
            creds.accessKeyId(),
            creds.secretAccessKey(),
            creds.sessionToken(),
            awsRegion
        );
        
        log.debug("Returning CredentialsResponse: accessKeyId={}, region={}, hasSessionToken={}", 
            credResp.getAccessKeyId(), 
            credResp.getRegion(),
            credResp.getSessionToken() != null && !credResp.getSessionToken().isEmpty());
        
        return credResp;

    } catch (Exception ex) {
        log.error("STS GetSessionToken failed: {}", ex.getMessage(), ex);
        throw new RuntimeException("Failed to get temporary AWS credentials: " + ex.getMessage());
    }
    }

   
    public SessionResponse createSession() {
        try {
            CreateFaceLivenessSessionRequest request = CreateFaceLivenessSessionRequest.builder()
                    .settings(CreateFaceLivenessSessionRequestSettings.builder()
                            .auditImagesLimit(2)
                            .build())
                    .build();

            CreateFaceLivenessSessionResponse response =
                    rekognitionClient.createFaceLivenessSession(request);

            log.info("Created liveness session: {}", response.sessionId());
            return new SessionResponse(response.sessionId(), "Session created successfully");

        } catch (RekognitionException ex) {
            log.error("Failed to create liveness session: {}", ex.awsErrorDetails().errorMessage());
            throw ex;
        }
    }

    
    public LivenessResultResponse getResult(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new LivenessException("Session ID must not be blank.");
        }

        try {
            GetFaceLivenessSessionResultsRequest request =
                    GetFaceLivenessSessionResultsRequest.builder()
                            .sessionId(sessionId)
                            .build();

            GetFaceLivenessSessionResultsResponse response =
                    rekognitionClient.getFaceLivenessSessionResults(request);

            double confidence = response.confidence() != null ? response.confidence() : 0.0;
            boolean isLive = confidence >= confidenceThreshold;
            String status = response.statusAsString();

            log.info("Liveness result — Session: {} | Status: {} | Confidence: {} | Live: {}",
                    sessionId, status, confidence, isLive);

            return LivenessResultResponse.builder()
                    .sessionId(sessionId)
                    .status(status)
                    .confidence(confidence)
                    .isLive(isLive)
                    .message(isLive ? "Identity verified successfully." : "Liveness not confirmed.")
                    .build();

        } catch (RekognitionException ex) {
            log.error("Failed to get liveness result for session {}: {}",
                    sessionId, ex.awsErrorDetails().errorMessage());
            throw ex;
        }
    }

    
    public LivenessUploadResponse saveVideo(MultipartFile file, String sessionId, String timestamp) {
        try {
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "kyc-" + sessionId + "-" + UUID.randomUUID() + ".webm";
            Path filePath = uploadPath.resolve(filename);
            file.transferTo(filePath.toFile());

            log.info("Video saved — Session: {} | File: {} | Size: {} bytes",
                    sessionId, filename, file.getSize());

            return LivenessUploadResponse.builder()
                    .success(true)
                    .fileName(filename)
                    .sessionId(sessionId)
                    .timestamp(timestamp)
                    .size(file.getSize())
                    .message("Video uploaded successfully.")
                    .build();

        } catch (IOException ex) {
            log.error("Failed to save video for session {}: {}", sessionId, ex.getMessage());
            throw new LivenessException("Failed to save verification video.", ex);
        }
    }
}
