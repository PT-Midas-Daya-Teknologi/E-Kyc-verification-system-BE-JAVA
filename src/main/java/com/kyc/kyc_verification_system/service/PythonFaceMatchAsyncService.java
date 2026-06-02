package com.kyc.kyc_verification_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class PythonFaceMatchAsyncService {

    private static final Logger log = LoggerFactory.getLogger(PythonFaceMatchAsyncService.class);

    private final PythonApiService pythonApiService;
    private final ConcurrentHashMap<String, Boolean> started = new ConcurrentHashMap<>();

    public PythonFaceMatchAsyncService(PythonApiService pythonApiService) {
        this.pythonApiService = pythonApiService;
    }

    @Async
    public void runFaceMatch(
            String cacheKey,
            String kycSessionId,
            byte[] auditImageBytes,
            int attemptNo,
            Consumer<PythonCheckResultResponse> onSuccess) {

        if (started.putIfAbsent(cacheKey, Boolean.TRUE) != null) {
            return;
        }

        try {
            PythonCheckResultResponse posted =
                    pythonApiService.postCheckResult(kycSessionId, auditImageBytes, attemptNo);

            if (isResolvedResult(posted)) {
                onSuccess.accept(posted);
                log.info("Async Python face match completed for KYC session {}", kycSessionId);
                return;
            }

            PythonCheckResultResponse fromGet = pythonApiService.fetchCheckResult(kycSessionId);
            if (isResolvedResult(fromGet)) {
                onSuccess.accept(fromGet);
                log.info("Async Python face match resolved via GET for KYC session {}", kycSessionId);
                return;
            }

            log.warn(
                    "Python face match returned no final result for KYC session {} — caching rejection",
                    kycSessionId);
            onSuccess.accept(buildRejectedFallback(kycSessionId, attemptNo));
        } catch (Exception ex) {
            log.warn("Async Python face match failed for KYC session {}: {}", kycSessionId, ex.getMessage());
            onSuccess.accept(buildRejectedFallback(kycSessionId, attemptNo));
        }
    }

    private boolean isResolvedResult(PythonCheckResultResponse result) {
        return result != null
                && result.getFinalResult() != null
                && !"PENDING".equalsIgnoreCase(result.getFinalResult());
    }

    private PythonCheckResultResponse buildRejectedFallback(String kycSessionId, int attemptNo) {
        PythonCheckResultResponse fallback = new PythonCheckResultResponse();
        fallback.setSessionId(kycSessionId);
        fallback.setAttemptNo(attemptNo);
        fallback.setConfidence(0.0);
        fallback.setVerified(false);
        fallback.setFaceScore("0.0");
        fallback.setFinalResult("REJECTED");
        return fallback;
    }
}