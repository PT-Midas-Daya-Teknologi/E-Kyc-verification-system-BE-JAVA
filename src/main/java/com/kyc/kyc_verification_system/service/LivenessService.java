
package com.kyc.kyc_verification_system.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.kyc.kyc_verification_system.dto.AwsLivenessResultDto;
import com.kyc.kyc_verification_system.dto.CredentialsResponse;
import com.kyc.kyc_verification_system.dto.LivenessResultResponse;
import com.kyc.kyc_verification_system.dto.LivenessUploadResponse;
import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;
import com.kyc.kyc_verification_system.dto.PythonFaceMatchResultDto;
import com.kyc.kyc_verification_system.dto.SessionResponse;
import com.kyc.kyc_verification_system.exception.LivenessException;
import com.kyc.kyc_verification_system.mapper.LivenessResponseMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.AuditImage;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequestSettings;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

@Service
public class LivenessService {

    private static final Logger log = LoggerFactory.getLogger(LivenessService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RekognitionClient rekognitionClient;
    private final StsClient stsClient;
    private final PythonFaceMatchOrchestrator pythonFaceMatchOrchestrator;
    private final UserSessionAttemptService userSessionAttemptService;
    private final LivenessResponseMapper livenessResponseMapper;
    private final ObjectMapper objectMapper;
    private final PythonApiService pythonApiService;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${liveness.confidence.threshold:90.0}")
    private double confidenceThreshold;

    @Value("${upload.directory:uploads}")
    private String uploadDirectory;

    public LivenessService(
            RekognitionClient rekognitionClient,
            StsClient stsClient,
            PythonFaceMatchOrchestrator pythonFaceMatchOrchestrator,
            UserSessionAttemptService userSessionAttemptService,
            LivenessResponseMapper livenessResponseMapper,
            ObjectMapper objectMapper,
            PythonApiService pythonApiService) {
        this.rekognitionClient = rekognitionClient;
        this.stsClient = stsClient;
        this.pythonFaceMatchOrchestrator = pythonFaceMatchOrchestrator;
        this.userSessionAttemptService = userSessionAttemptService;
        this.livenessResponseMapper = livenessResponseMapper;
        this.objectMapper = objectMapper;
        this.pythonApiService = pythonApiService;
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

            return new CredentialsResponse(
                    creds.accessKeyId(),
                    creds.secretAccessKey(),
                    creds.sessionToken(),
                    awsRegion);
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

    public LivenessResultResponse getResult(String awsSessionId, UUID kycSessionId) {
        if (awsSessionId == null || awsSessionId.isBlank()) {
            throw new LivenessException("AWS session ID must not be blank.");
        }
        if (kycSessionId == null) {
            throw new LivenessException("KYC session ID must not be blank.");
        }

        userSessionAttemptService.requireActiveSession(kycSessionId);

        try {
            GetFaceLivenessSessionResultsResponse awsRawResponse =
                    rekognitionClient.getFaceLivenessSessionResults(
                            GetFaceLivenessSessionResultsRequest.builder()
                                    .sessionId(awsSessionId)
                                    .build());

            double confidence = awsRawResponse.confidence() != null ? awsRawResponse.confidence() : 0.0;
            boolean isLive = confidence >= confidenceThreshold;
            String awsStatus = awsRawResponse.statusAsString();

            AwsLivenessResultDto awsResult =
                    livenessResponseMapper.toAwsResult(awsSessionId, awsRawResponse, confidence, isLive);

            log.info(
                    "Liveness result — KYC: {} | AWS: {} | Status: {} | Confidence: {} | Live: {}",
                    kycSessionId, awsSessionId, awsStatus, confidence, isLive);

            if (isProcessing(awsStatus)) {
                return buildInProgressResponse(awsSessionId, kycSessionId, awsResult, awsStatus, confidence, isLive);
            }

            boolean awsSuccess = "SUCCEEDED".equals(awsStatus) && isLive;

            if (!awsSuccess) {
                return handleAwsFailure(kycSessionId, awsSessionId, awsResult, awsStatus, confidence, isLive);
            }

            return handleAwsSuccess(
                    kycSessionId,
                    awsSessionId,
                    awsRawResponse,
                    awsResult,
                    awsStatus,
                    confidence,
                    isLive);

        } catch (RekognitionException ex) {
            log.error("Failed to get liveness result for session {}: {}",
                    awsSessionId, ex.awsErrorDetails().errorMessage());
            throw ex;
        }
    }

    private boolean isProcessing(String awsStatus) {
        return !"SUCCEEDED".equals(awsStatus) && !"FAILED".equals(awsStatus);
    }

    private LivenessResultResponse handleAwsFailure(
            UUID kycSessionId,
            String awsSessionId,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive) {

        if (!userSessionAttemptService.hasAttemptForAwsSession(kycSessionId, awsSessionId)) {
            int nextAttemptNo = userSessionAttemptService.getAttemptCount(kycSessionId) + 1;
            userSessionAttemptService.appendAttempt(
                    kycSessionId,
                    livenessResponseMapper.buildAttemptRecord(
                            nextAttemptNo,
                            awsResult,
                            null,
                            LivenessResponseMapper.OVERALL_FAILURE));
        }

        int attemptCount = userSessionAttemptService.getAttemptCount(kycSessionId);

        return LivenessResultResponse.builder()
                .sessionId(awsSessionId)
                .overallStatus(LivenessResponseMapper.OVERALL_FAILURE)
                .attemptCount(attemptCount)
                .maxAttempts(MAX_ATTEMPTS)
                .awsResponse(awsResult)
                .pythonResponse(null)
                .status(awsStatus)
                .confidence(confidence)
                .isLive(isLive)
                .message("AWS liveness verification failed.")
                .livenessStatus(formatLivenessStatus(awsStatus, confidence, isLive))
                .faceScore("N/A")
                .finalResult("REJECTED")
                .build();
    }

    private LivenessResultResponse handleAwsSuccess(
            UUID kycSessionId,
            String awsSessionId,
            GetFaceLivenessSessionResultsResponse awsRawResponse,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive) {

        int attemptCount = userSessionAttemptService.getAttemptCount(kycSessionId);

        if (userSessionAttemptService.hasAttemptForAwsSession(kycSessionId, awsSessionId)) {
            return buildResponseFromExistingAttempt(
                    kycSessionId, awsSessionId, awsResult, awsStatus, confidence, isLive);
        }

        String cacheKey = pythonCacheKey(kycSessionId, awsSessionId);
        int nextAttemptNo = attemptCount + 1;
        byte[] auditImageBytes = extractAuditImageBytes(awsRawResponse);

        PythonCheckResultResponse pythonRaw = pythonFaceMatchOrchestrator.resolve(
                kycSessionId, cacheKey, auditImageBytes, nextAttemptNo);

        if (!isResolvedPythonResult(pythonRaw)) {
            if (auditImageBytes == null || auditImageBytes.length == 0) {
                log.warn(
                        "No audit image for AWS session {} — cannot run face match",
                        awsSessionId);
            } else {
                log.warn(
                        "Python face match returned no result for AWS session {} — treating as rejected",
                        awsSessionId);
            }
            return finalizeAwsSuccess(
                    kycSessionId,
                    awsSessionId,
                    awsResult,
                    awsStatus,
                    confidence,
                    isLive,
                    buildRejectedPythonResult(kycSessionId, nextAttemptNo),
                    nextAttemptNo);
        }

        return finalizeAwsSuccess(
                kycSessionId,
                awsSessionId,
                awsResult,
                awsStatus,
                confidence,
                isLive,
                pythonRaw,
                nextAttemptNo);
    }

    private LivenessResultResponse buildResponseFromExistingAttempt(
            UUID kycSessionId,
            String awsSessionId,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive) {

        Map<String, Object> existingAttempt =
                userSessionAttemptService.findAttemptForAwsSession(kycSessionId, awsSessionId);
        PythonFaceMatchResultDto pythonResult = resolvePythonFromLastAttempt(kycSessionId, awsSessionId);

        String overallStatus = LivenessResponseMapper.OVERALL_FAILURE;
        if (existingAttempt != null && existingAttempt.get("overallStatus") != null) {
            overallStatus = String.valueOf(existingAttempt.get("overallStatus"));
        }

        boolean pythonSuccess = LivenessResponseMapper.OVERALL_SUCCESS.equals(overallStatus);
        int attemptCount = userSessionAttemptService.getAttemptCount(kycSessionId);

        return buildFinalAwsSuccessResponse(
                awsSessionId,
                awsResult,
                awsStatus,
                confidence,
                isLive,
                pythonResult,
                pythonSuccess,
                overallStatus,
                attemptCount);
    }

    private LivenessResultResponse finalizeAwsSuccess(
            UUID kycSessionId,
            String awsSessionId,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive,
            PythonCheckResultResponse pythonRaw,
            int attemptNo) {

        PythonFaceMatchResultDto pythonResult = livenessResponseMapper.toPythonResult(pythonRaw);
        boolean pythonSuccess = isPythonSuccess(pythonRaw);
        String overallStatus = pythonSuccess
                ? LivenessResponseMapper.OVERALL_SUCCESS
                : LivenessResponseMapper.OVERALL_FAILURE;

        userSessionAttemptService.appendAttempt(
                kycSessionId,
                livenessResponseMapper.buildAttemptRecord(
                        attemptNo,
                        awsResult,
                        pythonResult,
                        overallStatus));

        int attemptCount = userSessionAttemptService.getAttemptCount(kycSessionId);

        return buildFinalAwsSuccessResponse(
                awsSessionId,
                awsResult,
                awsStatus,
                confidence,
                isLive,
                pythonResult,
                pythonSuccess,
                overallStatus,
                attemptCount);
    }

    private LivenessResultResponse buildFinalAwsSuccessResponse(
            String awsSessionId,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive,
            PythonFaceMatchResultDto pythonResult,
            boolean pythonSuccess,
            String overallStatus,
            int attemptCount) {

        String faceScore = pythonResult != null && pythonResult.getFaceScore() != null
                ? pythonResult.getFaceScore()
                : "N/A";
        String finalResult = pythonResult != null && pythonResult.getFinalResult() != null
                ? pythonResult.getFinalResult()
                : "REJECTED";

        if (pythonSuccess) {
            return LivenessResultResponse.builder()
                    .sessionId(awsSessionId)
                    .overallStatus(LivenessResponseMapper.OVERALL_SUCCESS)
                    .attemptCount(attemptCount)
                    .maxAttempts(MAX_ATTEMPTS)
                    .awsResponse(awsResult)
                    .pythonResponse(pythonResult)
                    .status(awsStatus)
                    .confidence(confidence)
                    .isLive(isLive)
                    .message("Identity verified successfully.")
                    .livenessStatus(formatLivenessStatus(awsStatus, confidence, isLive))
                    .faceScore(faceScore)
                    .finalResult(finalResult)
                    .build();
        }

        String failureMessage = "TIMEOUT".equalsIgnoreCase(finalResult)
                ? "Face match verification timed out after 5 minutes. Please try again."
                : "Face match verification failed.";

        return LivenessResultResponse.builder()
                .sessionId(awsSessionId)
                .overallStatus(LivenessResponseMapper.OVERALL_FAILURE)
                .attemptCount(attemptCount)
                .maxAttempts(MAX_ATTEMPTS)
                .awsResponse(awsResult)
                .pythonResponse(pythonResult)
                .status(awsStatus)
                .confidence(confidence)
                .isLive(isLive)
                .message(failureMessage)
                .livenessStatus(formatLivenessStatus(awsStatus, confidence, isLive))
                .faceScore(faceScore)
                .finalResult(finalResult)
                .build();
    }

    private PythonCheckResultResponse buildRejectedPythonResult(UUID kycSessionId, int attemptNo) {
        PythonCheckResultResponse rejected = new PythonCheckResultResponse();
        rejected.setSessionId(kycSessionId.toString());
        rejected.setAttemptNo(attemptNo);
        rejected.setConfidence(0.0);
        rejected.setVerified(false);
        rejected.setFaceScore("0.0");
        rejected.setFinalResult("REJECTED");
        return rejected;
    }

    private boolean isResolvedPythonResult(PythonCheckResultResponse result) {
        return PythonApiService.isTerminal(result);
    }

    private String pythonCacheKey(UUID kycSessionId, String awsSessionId) {
        return kycSessionId + ":" + awsSessionId;
    }

    private LivenessResultResponse buildInProgressResponse(
            String awsSessionId,
            UUID kycSessionId,
            AwsLivenessResultDto awsResult,
            String awsStatus,
            double confidence,
            boolean isLive) {

        return LivenessResultResponse.builder()
                .sessionId(awsSessionId)
                .overallStatus(LivenessResponseMapper.OVERALL_IN_PROGRESS)
                .attemptCount(userSessionAttemptService.getAttemptCount(kycSessionId))
                .maxAttempts(MAX_ATTEMPTS)
                .awsResponse(awsResult)
                .pythonResponse(null)
                .status(awsStatus)
                .confidence(confidence)
                .isLive(isLive)
                .message("Liveness verification is still in progress.")
                .livenessStatus(formatLivenessStatus(awsStatus, confidence, isLive))
                .faceScore("N/A")
                .finalResult("PENDING")
                .build();
    }

    private boolean isPythonSuccess(PythonCheckResultResponse pythonRaw) {
        return pythonRaw != null
                && pythonRaw.getFinalResult() != null
                && "VERIFIED".equalsIgnoreCase(pythonRaw.getFinalResult());
    }

    private PythonFaceMatchResultDto resolvePythonFromLastAttempt(UUID kycSessionId, String awsSessionId) {
        Map<String, Object> attempt =
                userSessionAttemptService.findAttemptForAwsSession(kycSessionId, awsSessionId);
        if (attempt == null) {
            return null;
        }

        Object python = attempt.get("python");
        if (python == null) {
            return null;
        }

        return objectMapper.convertValue(python, PythonFaceMatchResultDto.class);
    }

    private String formatLivenessStatus(String status, double confidence, boolean isLive) {
        return String.format("Status: %s | Confidence: %s | Live: %s", status, confidence, isLive);
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

    public LivenessUploadResponse processSnapshot(
            MultipartFile snapshot,
            String sessionId,
            String kycSessionId,
            String timestamp) {
        try {
            log.info("🔷 processSnapshot START ——————————————————————————————————");
            log.info("  📥 Received snapshot upload request");
            log.info("     • sessionId: {}", sessionId);
            log.info("     • kycSessionId: {}", kycSessionId);
            log.info("     • snapshotSize: {} bytes", snapshot.getSize());
            log.info("     • contentType: {}", snapshot.getContentType());
            log.info("     • timestamp: {}", timestamp);

            // Save snapshot locally
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("  📁 Created upload directory: {}", uploadPath);
            }

            String filename = "snapshot-" + sessionId + "-" + UUID.randomUUID() + ".jpg";
            Path filePath = uploadPath.resolve(filename);
            snapshot.transferTo(filePath.toFile());

            log.info("  💾 Snapshot saved to disk");
            log.info("     • fileName: {}", filename);
            log.info("     • filePath: {}", filePath.toAbsolutePath());
            log.info("     • fileSize: {} bytes", Files.size(filePath));

            // Convert to bytes for Python API
            byte[] snapshotBytes = snapshot.getBytes();
            log.info("  🔄 Converted snapshot to byte array: {} bytes", snapshotBytes.length);

            // Forward to Python API for face verification
            UUID kycSessionUUID = UUID.fromString(kycSessionId);
            int attemptNo = userSessionAttemptService.getAttemptCount(kycSessionUUID) + 1;

            log.info("  🚀 FORWARDING TO PYTHON API");
            log.info("     • endpoint: /check_result");
            log.info("     • sessionId: {}", kycSessionId);
            log.info("     • attemptNo: {}", attemptNo);
            log.info("     • imageBytes: {} bytes", snapshotBytes.length);

            PythonCheckResultResponse pythonResponse = pythonApiService.postCheckResult(
                    kycSessionId,
                    snapshotBytes,
                    attemptNo);

            if (pythonResponse != null) {
                log.info("  ✅ PYTHON API RESPONSE RECEIVED");
                log.info("     • sessionId: {}", pythonResponse.getSessionId());
                log.info("     • attemptNo: {}", pythonResponse.getAttemptNo());
                log.info("     • faceScore: {}", pythonResponse.getFaceScore());
                log.info("     • confidence: {}", pythonResponse.getConfidence());
                log.info("     • verified: {}", pythonResponse.getVerified());
                log.info("     • finalResult: {}", pythonResponse.getFinalResult());
            } else {
                log.warn("  ⚠️ NO RESPONSE FROM PYTHON API (null response)");
            }

            log.info("  ✅ Snapshot processing completed successfully");
            log.info("🔷 processSnapshot END ——————————————————————————————————");

            return LivenessUploadResponse.builder()
                    .success(true)
                    .fileName(filename)
                    .sessionId(sessionId)
                    .timestamp(timestamp)
                    .size(snapshot.getSize())
                    .message("Snapshot uploaded and processed successfully.")
                    .build();

        } catch (IOException ex) {
            log.error("❌ IOException during snapshot processing for session {}: {}", sessionId, ex.getMessage(), ex);
            throw new LivenessException("Failed to process verification snapshot.", ex);
        } catch (Exception ex) {
            log.error("❌ Exception during snapshot processing for session {}: {}", sessionId, ex.getMessage(), ex);
            throw new LivenessException("Failed to process verification snapshot: " + ex.getMessage(), ex);
        }
    }
}
