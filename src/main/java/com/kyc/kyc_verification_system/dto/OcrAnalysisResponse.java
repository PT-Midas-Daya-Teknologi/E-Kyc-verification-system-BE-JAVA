package com.kyc.kyc_verification_system.dto;


import lombok.Data;

@Data
public class OcrAnalysisResponse {

    private String text;

    private int left;

    private int top;

    private int width;

    private int height;

    private int confidence;
}