package com.kyc.liveness.dto;

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
