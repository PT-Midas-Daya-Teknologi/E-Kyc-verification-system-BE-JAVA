package com.kyc.kyc_verification_system.mapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.kyc_verification_system.dto.AwsLivenessResultDto;
import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;
import com.kyc.kyc_verification_system.dto.PythonFaceMatchResultDto;

import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;

@Component
public class LivenessResponseMapper {

    public static final String OVERALL_SUCCESS = "SUCCESS";
    public static final String OVERALL_FAILURE = "FAILURE";
    public static final String OVERALL_IN_PROGRESS = "IN_PROGRESS";

    private final ObjectMapper objectMapper;

    public LivenessResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AwsLivenessResultDto toAwsResult(
            String awsSessionId,
            GetFaceLivenessSessionResultsResponse response,
            double confidence,
            boolean isLive) {

        String status = response.statusAsString();
        String message = isLive
                ? "AWS liveness verified successfully."
                : "AWS liveness not confirmed.";

        return AwsLivenessResultDto.builder()
                .sessionId(awsSessionId)
                .status(status)
                .confidence(confidence)
                .isLive(isLive)
                .message(message)
                .build();
    }

    public PythonFaceMatchResultDto toPythonResult(PythonCheckResultResponse response) {
        if (response == null) {
            return null;
        }

        return PythonFaceMatchResultDto.builder()
                .sessionId(response.getSessionId())
                .attemptNo(response.getAttemptNo())
                .confidence(response.getConfidence())
                .verified(response.getVerified())
                .faceScore(response.getFaceScore())
                .finalResult(response.getFinalResult())
                .build();
    }

    public Map<String, Object> buildAttemptRecord(
            int attemptNo,
            AwsLivenessResultDto aws,
            PythonFaceMatchResultDto python,
            String overallStatus) {

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("attemptNo", attemptNo);
        record.put("timestamp", Instant.now().toString());
        record.put("overallStatus", overallStatus);
        record.put("aws", aws);
        record.put("python", python);
        return record;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize liveness response", ex);
        }
    }
}
