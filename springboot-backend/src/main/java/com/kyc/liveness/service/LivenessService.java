package com.kyc.liveness.service;

import com.kyc.liveness.dto.CredentialsResponse;
import com.kyc.liveness.dto.LivenessResultResponse;
import com.kyc.liveness.dto.PythonCheckResultResponse;
import com.kyc.liveness.dto.SessionResponse;
import com.kyc.liveness.dto.UploadResponse;
import com.kyc.liveness.exception.LivenessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequestSettings;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.model.AuditImage;
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
import java.util.concurrent.ConcurrentHashMap;
import com.kyc.liveness.entity.LivenessResult;
import com.kyc.liveness.repository.LivenessResultRepository;
/**
 * Service layer for all Face Liveness operations.
 * Communicates with AWS Rekognition and STS via AWS SDK v2.
 */
@Service
public class LivenessService {

    private static final Logger log = LoggerFactory.getLogger(LivenessService.class);

    private final ConcurrentHashMap<String, PythonCheckResultResponse> pythonResultCache =
            new ConcurrentHashMap<>();
    private final LivenessResultRepository repository;

    private final RekognitionClient rekognitionClient;
    private final StsClient stsClient;
    private final PythonApiService pythonApiService;
    private final PythonFaceMatchAsyncService pythonFaceMatchAsyncService;

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

    public LivenessService(
            RekognitionClient rekognitionClient,
            StsClient stsClient,
            LivenessResultRepository repository,
            PythonApiService pythonApiService,
            PythonFaceMatchAsyncService pythonFaceMatchAsyncService) {
        this.rekognitionClient = rekognitionClient;
        this.stsClient = stsClient;
        this.repository = repository;
        this.pythonApiService = pythonApiService;
        this.pythonFaceMatchAsyncService = pythonFaceMatchAsyncService;
    }

    /**
     * Returns short-lived AWS credentials for the React FaceLivenessDetector.
     *
     * Strategy:
     *  1. Try STS GetSessionToken (works for IAM users WITHOUT MFA).
     *  2. If STS fails (e.g. MFA required, or root account), fall back to
     *     returning the long-term IAM credentials directly.
     *
     * The React FaceLivenessDetector uses these as its credentialProvider,
     * so NO Cognito / Identity Pool is needed on the frontend.
     */
    public CredentialsResponse getTemporaryCredentials() {
    try {
        GetSessionTokenRequest request = GetSessionTokenRequest.builder()
            .durationSeconds(3600) // 1 hour
            .build();

        GetSessionTokenResponse response = stsClient.getSessionToken(request);
        var creds = response.credentials();

        log.info("Issued STS temporary credentials, expiry: {}", creds.expiration());
        
        // Validate all fields before returning
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

    /**
     * Creates a new Face Liveness session via AWS Rekognition.
     *
     * @return SessionResponse containing the sessionId
     */
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

    /**
     * Fetches the liveness result for a completed session from AWS Rekognition.
     * Evaluates confidence against the configured threshold.
     *
     * @param sessionId the session ID returned from createSession
     * @return LivenessResultResponse with confidence score and isLive flag
     */
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

            /*
             * SAVE RESULT INTO DATABASE
             */
            LivenessResult result = new LivenessResult();

            result.setSessionId(sessionId);
            result.setStatus(status);
            result.setConfidence(confidence);
            result.setLive(isLive);

            repository.save(result);
            
            log.info("Liveness result — Session: {} | Status: {} | Confidence: {} | Live: {}",
                    sessionId, status, confidence, isLive);

            String livenessStatus = String.format(
                    "Status: %s | Confidence: %s | Live: %s", status, confidence, isLive);

            String faceScore = "N/A";
            String finalResult = "PENDING";

            if ("SUCCEEDED".equals(status)) {
                PythonCheckResultResponse pythonResult =
                        resolvePythonCheckResult(sessionId, response, confidence, isLive);
                if (pythonResult != null) {
                    if (pythonResult.getFaceScore() != null && !pythonResult.getFaceScore().isBlank()) {
                        faceScore = pythonResult.getFaceScore();
                    }
                    if (pythonResult.getFinalResult() != null && !pythonResult.getFinalResult().isBlank()) {
                        finalResult = pythonResult.getFinalResult();
                    }
                }
            }

            return LivenessResultResponse.builder()
                    .sessionId(sessionId)
                    .status(status)
                    .confidence(confidence)
                    .isLive(isLive)
                    .message(isLive ? "Identity verified successfully." : "Liveness not confirmed.")
                    .livenessStatus(livenessStatus)
                    .faceScore(faceScore)
                    .finalResult(finalResult)
                    .build();

        } catch (RekognitionException ex) {
            log.error("Failed to get liveness result for session {}: {}",
                    sessionId, ex.awsErrorDetails().errorMessage());
            throw ex;
        }
    }

    /**
     * Resolves face-match data without blocking on slow DeepFace POST.
     * Returns cached or fast GET result; falls back to AWS confidence; runs POST async once.
     */
    private PythonCheckResultResponse resolvePythonCheckResult(
            String sessionId,
            GetFaceLivenessSessionResultsResponse awsResponse,
            double confidence,
            boolean isLive) {

        PythonCheckResultResponse cached = pythonResultCache.get(sessionId);
        if (cached != null) {
            return cached;
        }

        PythonCheckResultResponse fromPython = pythonApiService.fetchCheckResult(sessionId);
        if (isResolvedPythonResult(fromPython)) {
            pythonResultCache.put(sessionId, fromPython);
            return fromPython;
        }

        PythonCheckResultResponse fallback = buildAwsFallback(confidence, isLive);
        pythonResultCache.putIfAbsent(sessionId, fallback);

        byte[] auditImageBytes = extractAuditImageBytes(awsResponse);
        if (auditImageBytes != null && auditImageBytes.length > 0) {
            pythonFaceMatchAsyncService.runFaceMatch(
                    sessionId,
                    auditImageBytes,
                    posted -> pythonResultCache.put(sessionId, posted));
        }

        return pythonResultCache.get(sessionId);
    }

    private boolean isResolvedPythonResult(PythonCheckResultResponse result) {
        return result != null
                && result.getFinalResult() != null
                && !"PENDING".equalsIgnoreCase(result.getFinalResult());
    }

    private PythonCheckResultResponse buildAwsFallback(double confidence, boolean isLive) {
        return new PythonCheckResultResponse(
                String.format("%.1f", confidence),
                isLive ? "VERIFIED" : "REJECTED");
    }

    private byte[] extractAuditImageBytes(GetFaceLivenessSessionResultsResponse response) {
        if (response.referenceImage() != null) {
            byte[] bytes = imageBytesFromAuditImage(response.referenceImage());
            if (bytes != null) {
                return bytes;
            }
        }

        if (response.auditImages() != null) {
            for (AuditImage auditImage : response.auditImages()) {
                byte[] bytes = imageBytesFromAuditImage(auditImage);
                if (bytes != null) {
                    return bytes;
                }
            }
        }

        return null;
    }

    private byte[] imageBytesFromAuditImage(AuditImage auditImage) {
        if (auditImage == null) {
            return null;
        }
        SdkBytes bytes = auditImage.bytes();
        if (bytes == null) {
            return null;
        }
        return bytes.asByteArray();
    }

    /**
     * Saves the uploaded verification video to the local uploads directory.
     *
     * @param file      the WebM video file
     * @param sessionId the associated session ID
     * @param timestamp ISO timestamp from the client
     * @return UploadResponse with file metadata
     */
    public UploadResponse saveVideo(MultipartFile file, String sessionId, String timestamp) {
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

            return new UploadResponse(
                    true, filename, sessionId, timestamp, file.getSize(), "Video uploaded successfully.");

        } catch (IOException ex) {
            log.error("Failed to save video for session {}: {}", sessionId, ex.getMessage());
            throw new LivenessException("Failed to save verification video.", ex);
        }
    }
}
