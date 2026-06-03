package com.kyc.kyc_verification_system.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;

/**
 * Triggers Python face-match via POST /check_result and returns the result directly.
 */
@Service
public class PythonFaceMatchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PythonFaceMatchOrchestrator.class);

    private final PythonApiService pythonApiService;
    private final ConcurrentHashMap<String, PythonCheckResultResponse> completedCache =
            new ConcurrentHashMap<>();

    public PythonFaceMatchOrchestrator(PythonApiService pythonApiService) {
        this.pythonApiService = pythonApiService;
    }

    /**
     * Posts the audit image to Python POST /check_result and returns the result.
     * Caches the result so repeated calls for the same cacheKey return immediately.
     *
     * @return resolved Python result, or {@code null} if image is missing or POST failed.
     */
    public PythonCheckResultResponse resolve(
            UUID kycSessionId,
            String cacheKey,
            byte[] auditImageBytes,
            int attemptNo) {

        PythonCheckResultResponse cached = completedCache.get(cacheKey);
        if (isResolved(cached)) {
            return cached;
        }

        if (auditImageBytes == null || auditImageBytes.length == 0) {
            log.warn("No audit image bytes for KYC session {} — skipping face match", kycSessionId);
            return null;
        }

        log.info("Calling Python POST /check_result for KYC session {}", kycSessionId);

        PythonCheckResultResponse result =
                pythonApiService.postCheckResult(kycSessionId.toString(), auditImageBytes, attemptNo);

        if (isResolved(result)) {
            completedCache.put(cacheKey, result);
            log.info("Python face match resolved for KYC session {} — result: {}",
                    kycSessionId, result.getFinalResult());
            return result;
        }

        log.warn("Python POST /check_result returned no final result for KYC session {}", kycSessionId);
        return null;
    }

    private boolean isResolved(PythonCheckResultResponse result) {
        return PythonApiService.isTerminal(result);
    }
}
