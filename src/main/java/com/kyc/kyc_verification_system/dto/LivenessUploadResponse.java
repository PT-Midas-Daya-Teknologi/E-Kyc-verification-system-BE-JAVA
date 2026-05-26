package com.kyc.kyc_verification_system.dto;

import lombok.Builder;

@Builder
public class LivenessUploadResponse {
	
    private String fileName;
	
	private Boolean success;
    
    private String sessionId;
    
    private String timestamp;
    
    private Long size;
    
    private String message;

}
