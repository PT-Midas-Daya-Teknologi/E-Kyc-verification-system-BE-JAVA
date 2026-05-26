package com.kyc.kyc_verification_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CredentialsResponse {
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private String region;
}
