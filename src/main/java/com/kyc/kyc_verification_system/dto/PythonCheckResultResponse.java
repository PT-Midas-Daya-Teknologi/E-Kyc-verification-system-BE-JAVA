package com.kyc.kyc_verification_system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PythonCheckResultResponse {

    @JsonProperty("face_score")
    private String faceScore;

    @JsonProperty("final_result")
    private String finalResult;
}