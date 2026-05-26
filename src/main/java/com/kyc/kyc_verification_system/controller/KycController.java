package com.kyc.kyc_verification_system.controller;

import java.time.LocalDateTime;
import java.util.Collections;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kyc.kyc_verification_system.dto.CommonResponse;
import com.kyc.kyc_verification_system.dto.DocUploadResponse;
import com.kyc.kyc_verification_system.dto.ErrorResponse;
import com.kyc.kyc_verification_system.dto.InitiateResponse;
import com.kyc.kyc_verification_system.service.KycService;
import com.kyc.kyc_verification_system.util.JwtUtil;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/kyc")
public class KycController {

    private final KycService kycService;

    private final JwtUtil jwtUtil;

    public KycController(
            KycService kycService,
            JwtUtil jwtUtil) {

        this.kycService = kycService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CommonResponse<InitiateResponse>> initiateKyc(

            @RequestParam String username) {

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

            @RequestHeader(
                    value = "Authorization",
                    required = false)
            String authToken,

            @RequestParam(
                    value = "file",
                    required = false)
            MultipartFile file,

            @RequestParam(
                    value = "documentType",
                    required = false)
            String documentType) {

        try {

            if (authToken == null
                    || authToken.isBlank()
                    || !authToken.startsWith("Bearer ")) {

                return unauthorizedResponse(
                        "MISSING_TOKEN",
                        "Authorization token is missing"
                );
            }

            String token =
                    authToken.substring(7);

            if (!jwtUtil.validateToken(token)) {

                return unauthorizedResponse(
                        "INVALID_TOKEN",
                        "Invalid or expired token"
                );
            }

            String sessionId =
                    jwtUtil.extractSessionId(token);

            DocUploadResponse uploadResponse =
                    kycService.uploadDocument(
                            sessionId,
                            file,
                            documentType
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

    private ResponseEntity<CommonResponse<Object>> unauthorizedResponse(

            String code,
            String message) {

        ErrorResponse error =
                ErrorResponse.builder()
                        .code(code)
                        .status(401)
                        .message(message)
                        .build();

        CommonResponse<Object> errorResponse =
                CommonResponse.builder()
                        .success(false)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.status(401)
                .body(errorResponse);
    }
}