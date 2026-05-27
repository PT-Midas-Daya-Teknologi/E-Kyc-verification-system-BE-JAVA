package com.kyc.kyc_verification_system.dto;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class DocUploadRequest {

    private MultipartFile file;

    private String documentType;
}