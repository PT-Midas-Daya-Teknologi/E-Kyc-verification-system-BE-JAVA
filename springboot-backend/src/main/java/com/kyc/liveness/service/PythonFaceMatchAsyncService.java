package com.kyc.liveness.service;

import com.kyc.liveness.dto.PythonCheckResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
            String sessionId,
            byte[] auditImageBytes,
            Consumer<PythonCheckResultResponse> onSuccess) {

        if (started.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            return;
        }

        try {
            PythonCheckResultResponse posted =
                    pythonApiService.postCheckResult(sessionId, auditImageBytes);

            if (posted != null
                    && posted.getFinalResult() != null
                    && !"PENDING".equalsIgnoreCase(posted.getFinalResult())) {
                onSuccess.accept(posted);
                log.info("Async Python face match completed for session {}", sessionId);
            }
        } catch (Exception ex) {
            log.warn("Async Python face match failed for session {}: {}", sessionId, ex.getMessage());
        }
    }
}
