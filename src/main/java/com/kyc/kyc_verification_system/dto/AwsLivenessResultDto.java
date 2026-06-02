package com.kyc.kyc_verification_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwsLivenessResultDto {

    private String sessionId;
    private String status;
    private Double confidence;
    private Boolean isLive;
    private String message;
}
