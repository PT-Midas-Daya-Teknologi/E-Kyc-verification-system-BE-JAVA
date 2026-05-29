package com.kyc.liveness.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private Boolean success;
    private String filename;
    private String sessionId;
    private String timestamp;
    private Long size;
    private String message;
}
