package com.kyc.kyc_verification_system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LivenessResultResponse {

    private String sessionId;

  /** SUCCESS | FAILURE | IN_PROGRESS */
    private String overallStatus;

    private Integer attemptCount;

    private Integer maxAttempts;

    private AwsLivenessResultDto awsResponse;

    private PythonFaceMatchResultDto pythonResponse;

    /** Legacy fields kept for backward compatibility */
    private String status;
    private Double confidence;
    private Boolean isLive;
    private String message;

    @JsonProperty("liveness_status")
    private String livenessStatus;

    @JsonProperty("face_score")
    private String faceScore;

    @JsonProperty("final_result")
    private String finalResult;
}
