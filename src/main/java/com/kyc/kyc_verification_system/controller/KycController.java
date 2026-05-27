package com.kyc.kyc_verification_system.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.kyc.kyc_verification_system.dto.CommonResponse;
import com.kyc.kyc_verification_system.dto.DocUploadRequest;
import com.kyc.kyc_verification_system.dto.DocUploadResponse;
import com.kyc.kyc_verification_system.dto.ErrorResponse;
import com.kyc.kyc_verification_system.dto.InitiateResponse;
import com.kyc.kyc_verification_system.service.KycService;

@RestController
@RequestMapping("/kyc")
@CrossOrigin(origins = "http://localhost:3000")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {

        this.kycService = kycService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CommonResponse<InitiateResponse>> initiateKyc(
            String username) {

        InitiateResponse initiateResponse =
                kycService.initiateKyc(username);

        CommonResponse<InitiateResponse> response =
                CommonResponse.<InitiateResponse>builder()
                        .success(true)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.emptyList())
                        .body(initiateResponse)
                        .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(

            @RequestAttribute("sessionId")
            String sessionIdString,

            @ModelAttribute
            DocUploadRequest request) {

        try {

            UUID sessionId =
                    UUID.fromString(sessionIdString);

            DocUploadResponse uploadResponse =
                    kycService.uploadDocument(
                            sessionId,
                            request.getFile(),
                            request.getDocumentType()
                    );

            CommonResponse<DocUploadResponse> response =
                    CommonResponse.<DocUploadResponse>builder()
                            .success(true)
                            .timestamp(LocalDateTime.now().toString())
                            .errors(Collections.emptyList())
                            .body(uploadResponse)
                            .build();

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {

            ErrorResponse error =
                    ErrorResponse.builder()
                            .code("BAD_REQUEST")
                            .status(400)
                            .message(e.getMessage())
                            .build();

            CommonResponse<Object> errorResponse =
                    CommonResponse.builder()
                            .success(false)
                            .timestamp(LocalDateTime.now().toString())
                            .errors(Collections.singletonList(error))
                            .body(null)
                            .build();

            return ResponseEntity.badRequest()
                    .body(errorResponse);

        } catch (Exception e) {

            ErrorResponse error =
                    ErrorResponse.builder()
                            .code("INTERNAL_SERVER_ERROR")
                            .status(500)
                            .message("Something went wrong")
                            .build();

            CommonResponse<Object> errorResponse =
                    CommonResponse.builder()
                            .success(false)
                            .timestamp(LocalDateTime.now().toString())
                            .errors(Collections.singletonList(error))
                            .body(null)
                            .build();

            return ResponseEntity.internalServerError()
                    .body(errorResponse);
        }
    }
}