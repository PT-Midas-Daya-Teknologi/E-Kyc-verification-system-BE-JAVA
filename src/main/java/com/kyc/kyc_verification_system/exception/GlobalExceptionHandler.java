package com.kyc.kyc_verification_system.exception;

import java.time.LocalDateTime;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.kyc.kyc_verification_system.dto.CommonResponse;
import com.kyc.kyc_verification_system.dto.ErrorResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<Object>>
    handleMaxSizeException() {

        ErrorResponse error = ErrorResponse.builder()
                .code("FILE_SIZE_EXCEEDED")
                .status(400)
                .error("BAD_REQUEST")
                .message("Maximum allowed image size is 500 KB")
                .timestamp(LocalDateTime.now())
                .build();

        CommonResponse<Object> response =
                CommonResponse.builder()
                        .success(false)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Object>>
    handleNoResource(NoResourceFoundException ex) {

        ErrorResponse error = ErrorResponse.builder()
                .code("NOT_FOUND")
                .status(404)
                .error("NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        CommonResponse<Object> response =
                CommonResponse.builder()
                        .success(false)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CommonResponse<Object>>
    handleRuntimeException(RuntimeException ex) {

        log.error("Runtime error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .code("BAD_REQUEST")
                .status(400)
                .error("BAD_REQUEST")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        CommonResponse<Object> response =
                CommonResponse.builder()
                        .success(false)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Object>>
    handleGeneric(Exception ex) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .status(500)
                .error("INTERNAL_SERVER_ERROR")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        CommonResponse<Object> response =
                CommonResponse.builder()
                        .success(false)
                        .timestamp(LocalDateTime.now().toString())
                        .errors(Collections.singletonList(error))
                        .body(null)
                        .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}