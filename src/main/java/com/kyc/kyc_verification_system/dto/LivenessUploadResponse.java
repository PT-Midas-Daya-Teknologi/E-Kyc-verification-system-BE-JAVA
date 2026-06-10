package com.kyc.kyc_verification_system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LivenessUploadResponse {
	
    private String fileName;
	
	private Boolean success;
    
    private String sessionId;
    
    private String timestamp;
    
    private Long size;
    
    private String message;
    
    // Python API face verification response fields
    @JsonProperty("final_result")
    private String finalResult;
    
    private Double confidence;
    
    private Boolean verified;
    
    // Attempt information
    private Integer attemptNo;

}
