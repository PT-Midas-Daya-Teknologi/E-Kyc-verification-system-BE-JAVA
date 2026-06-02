package com.kyc.kyc_verification_system.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;

/**
 * Triggers Python face-match POST and polls GET until a terminal result (max 5 minutes).
 */
@Service
public class PythonFaceMatchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PythonFaceMatchOrchestrator.class);

    private final PythonApiService pythonApiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, CompletableFuture<PythonCheckResultResponse>> inflight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PythonCheckResultResponse> completedCache =
            new ConcurrentHashMap<>();

    public PythonFaceMatchOrchestrator(PythonApiService pythonApiService) {
        this.pythonApiService = pythonApiService;
    }

    /**
     * @return resolved Python result, or {@code null} while still processing.
     *
     * No fallback results are returned from Java — the frontend enforces the 5 minute timeout.
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

        String kycSessionIdStr = kycSessionId.toString();

        if (auditImageBytes == null || auditImageBytes.length == 0) {
            return null;
        }

        CompletableFuture<PythonCheckResultResponse> existing = inflight.get(cacheKey);
        if (existing == null) {
            inflight.computeIfAbsent(
                    cacheKey,
                    key -> CompletableFuture.supplyAsync(
                            () -> runPostAndPoll(kycSessionIdStr, auditImageBytes, attemptNo),
                            executor));
            return null;
        }

        if (!existing.isDone()) {
            PythonCheckResultResponse fromGet = pythonApiService.fetchCheckResult(kycSessionIdStr);
            if (isResolved(fromGet)) {
                completedCache.put(cacheKey, fromGet);
                return fromGet;
            }
            return null;
        }

        try {
            PythonCheckResultResponse result = existing.get();
            inflight.remove(cacheKey);
            if (isResolved(result)) {
                completedCache.put(cacheKey, result);
                return result;
            }
        } catch (Exception ex) {
            log.warn("Python face match future failed for KYC session {}: {}", kycSessionIdStr, ex.getMessage());
            inflight.remove(cacheKey);
        }

        return null;
    }

    private PythonCheckResultResponse runPostAndPoll(
            String kycSessionId,
            byte[] auditImageBytes,
            int attemptNo) {

        log.info("Starting Python face match for KYC session {}", kycSessionId);

        pythonApiService
                .postCheckResultAsync(kycSessionId, auditImageBytes, attemptNo)
                .subscribe();

        PythonCheckResultResponse polled = pythonApiService.pollUntilResolved(kycSessionId);
        if (isResolved(polled)) {
            return polled;
        }

        log.warn("Python face match poll timed out for KYC session {}", kycSessionId);
        return null;
    }

    private boolean isResolved(PythonCheckResultResponse result) {
        return PythonApiService.isTerminal(result);
    }

    // Intentionally no Java-side fallback result
}
