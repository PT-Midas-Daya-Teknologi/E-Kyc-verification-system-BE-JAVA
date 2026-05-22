package com.kyc.kyc_verification_system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitiateResponse {


	private String expiresAt;
	
    private String token;
}