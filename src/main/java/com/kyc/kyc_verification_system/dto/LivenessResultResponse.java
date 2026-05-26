package com.kyc.kyc_verification_system.dto;

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
}
