package com.kyc.kyc_verification_system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonFaceMatchResultDto {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("attempt_no")
    private Integer attemptNo;

    private Double confidence;

    private Boolean verified;

    @JsonProperty("face_score")
    private String faceScore;

    @JsonProperty("final_result")
    private String finalResult;
}
