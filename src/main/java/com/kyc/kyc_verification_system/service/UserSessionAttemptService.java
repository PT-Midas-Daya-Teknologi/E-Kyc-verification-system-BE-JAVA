package com.kyc.kyc_verification_system.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.kyc_verification_system.entity.UserSession;
import com.kyc.kyc_verification_system.exception.LivenessException;
import com.kyc.kyc_verification_system.repository.UserSessionRepository;

@Service
public class UserSessionAttemptService {

    private static final TypeReference<List<Map<String, Object>>> ATTEMPTS_TYPE =
            new TypeReference<>() {};

    private final UserSessionRepository userSessionRepository;
    private final ObjectMapper objectMapper;

    public UserSessionAttemptService(
            UserSessionRepository userSessionRepository,
            ObjectMapper objectMapper) {
        this.userSessionRepository = userSessionRepository;
        this.objectMapper = objectMapper;
    }

    public UserSession requireActiveSession(UUID kycSessionId) {
        UserSession session = userSessionRepository.findById(kycSessionId)
                .orElseThrow(() -> new LivenessException("KYC session not found: " + kycSessionId));

        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new LivenessException("KYC session is no longer active.");
        }

        return session;
    }

    public int getAttemptCount(UUID kycSessionId) {
        return readAttempts(requireActiveSession(kycSessionId)).size();
    }

    public int appendAttempt(UUID kycSessionId, Map<String, Object> attemptRecord) {
        UserSession session = requireActiveSession(kycSessionId);
        List<Map<String, Object>> attempts = readAttempts(session);
        attempts.add(attemptRecord);
        session.setAttempts(writeAttempts(attempts));
        userSessionRepository.save(session);
        return attempts.size();
    }

    /**
     * Prevents duplicate attempt rows when the frontend polls getResult for the same AWS session.
     */
    public boolean hasAttemptForAwsSession(UUID kycSessionId, String awsSessionId) {
        if (awsSessionId == null || awsSessionId.isBlank()) {
            return false;
        }

        for (Map<String, Object> attempt : readAttempts(requireActiveSession(kycSessionId))) {
            Object aws = attempt.get("aws");
            if (aws instanceof Map<?, ?> awsMap) {
                Object recordedSessionId = awsMap.get("sessionId");
                if (awsSessionId.equals(String.valueOf(recordedSessionId))) {
                    return true;
                }
            }
        }

        return false;
    }

    public Map<String, Object> findAttemptForAwsSession(UUID kycSessionId, String awsSessionId) {
        for (Map<String, Object> attempt : readAttempts(requireActiveSession(kycSessionId))) {
            Object aws = attempt.get("aws");
            if (aws instanceof Map<?, ?> awsMap) {
                Object recordedSessionId = awsMap.get("sessionId");
                if (awsSessionId.equals(String.valueOf(recordedSessionId))) {
                    return attempt;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> readAttempts(UserSession session) {
        String raw = session.getAttempts();
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        try {
            List<Map<String, Object>> parsed =
                    objectMapper.readValue(raw, ATTEMPTS_TYPE);
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String writeAttempts(List<Map<String, Object>> attempts) {
        try {
            return objectMapper.writeValueAsString(attempts);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist session attempts", ex);
        }
    }
}
