package com.kyc.kyc_verification_system.dto;

import java.util.List;

import com.kyc.kyc_verification_system.dto.ErrorResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {

    private boolean success;

    private String timestamp;

    private List<ErrorResponse> errors;

    private T body;
    
    
}