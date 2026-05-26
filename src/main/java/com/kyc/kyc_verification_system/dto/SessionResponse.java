package com.kyc.kyc_verification_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionResponse {
    private String sessionId;
    private String message;
}
