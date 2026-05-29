package com.kyc.liveness.exception;

import com.kyc.liveness.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(LivenessException.class)
    public ResponseEntity<ErrorResponse> handleLiveness(LivenessException ex) {
        log.error("Liveness error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Liveness Error", ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(RekognitionException.class)
    public ResponseEntity<ErrorResponse> handleRekognition(RekognitionException ex) {
        String msg = ex.awsErrorDetails().errorMessage();
        log.error("Rekognition error: {}", msg);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "AWS Rekognition Error", msg, LocalDateTime.now()));
    }

    @ExceptionHandler(AwsServiceException.class)
    public ResponseEntity<ErrorResponse> handleAwsService(AwsServiceException ex) {
        String msg = ex.awsErrorDetails().errorMessage();
        log.error("AWS error: {}", msg);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "AWS Service Error", msg, LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Server Error", ex.getMessage(), LocalDateTime.now()));
    }
}
