package com.kyc.kyc_verification_system.exception;

import java.time.LocalDateTime;
import java.util.Collections;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.kyc.kyc_verification_system.dto.CommonResponse;
import com.kyc.kyc_verification_system.dto.ErrorResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<Object>> handleMaxSizeException() {

        ErrorResponse error = ErrorResponse.builder()
                .code("FILE_SIZE_EXCEEDED")
                .status("BAD_REQUEST")
                .message("Maximum allowed image size is 500 KB")
                .build();

        CommonResponse<Object> response =
                CommonResponse.builder()
                        .success(false)
                        .responseTime(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.badRequest().body(response);
    }
}