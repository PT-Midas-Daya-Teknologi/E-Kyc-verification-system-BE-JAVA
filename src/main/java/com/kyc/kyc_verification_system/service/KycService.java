package com.kyc.kyc_verification_system.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.kyc.kyc_verification_system.dto.DocUploadResponse;
import com.kyc.kyc_verification_system.dto.InitiateResponse;

import com.kyc.kyc_verification_system.entity.User;
import com.kyc.kyc_verification_system.entity.UserDocument;
import com.kyc.kyc_verification_system.entity.UserSession;

import com.kyc.kyc_verification_system.repository.UserDocumentRepository;
import com.kyc.kyc_verification_system.repository.UserRepository;
import com.kyc.kyc_verification_system.repository.UserSessionRepository;

import com.kyc.kyc_verification_system.util.JwtUtil;

@Service
@RequiredArgsConstructor
public class KycService {

    private final JwtUtil jwtUtil;

    private final UserDocumentRepository userDocumentRepository;

    private final UserRepository userRepository;

    private final UserSessionRepository userSessionRepository;

    private final WebClient webClient;

    public InitiateResponse initiateKyc(String username) {

        if (username == null || username.isBlank()) {

            throw new RuntimeException("Username is required");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("Invalid username"));

        UUID sessionId = UUID.randomUUID();

        LocalDateTime expiresAt =
                LocalDateTime.now().plusMinutes(10);

        UserSession session = new UserSession();

        session.setId(sessionId);

        session.setUserId(user.getId());

        session.setDocumentId(null);

        session.setVideoId(null);

        session.setAttempts("[]");

        session.setSessionExpiry(expiresAt);

        session.setIsActive(true);

        userSessionRepository.save(session);

        String token =
                jwtUtil.generateToken(sessionId.toString());

        return InitiateResponse.builder()
                .token(token)
                .expiresAt(expiresAt.toString())
                .build();
    }

    public DocUploadResponse uploadDocument(
            UUID sessionId,
            MultipartFile file,
            String documentType) throws Exception {

        validateFile(file);

        validateDocumentType(documentType);

        validateContentType(file);

        validateFileSize(file);

        String fileName =
                file.getOriginalFilename();

        String contentType =
                file.getContentType();

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder();

        bodyBuilder.part(
                "id_document_file",
                new ByteArrayResource(file.getBytes()) {

                    @Override
                    public String getFilename() {

                        return file.getOriginalFilename();
                    }
                })
                .contentType(
                        MediaType.parseMediaType(contentType)
                );

        String ocrResponse;

        try {

            ocrResponse = webClient.post()
                    .uri("/ocr_analysis")
                    .contentType(
                            MediaType.MULTIPART_FORM_DATA
                    )
                    .body(
                            BodyInserters.fromMultipartData(
                                    bodyBuilder.build()
                            )
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {

            throw new RuntimeException(
                    "OCR analysis failed"
            );
        }

        String base64File =
                Base64.getEncoder()
                        .encodeToString(file.getBytes());

        UserDocument document =
                new UserDocument();

        document.setSessionId(sessionId.toString());

        document.setType(documentType);

        document.setContent(base64File);

        document.setOcrData(ocrResponse);

        document.setCreatedAt(LocalDateTime.now());

        document.setCreatedBy("SYSTEM");

        document.setUpdatedAt(LocalDateTime.now());

        document.setUpdatedBy("SYSTEM");

        userDocumentRepository.save(document);

        return DocUploadResponse.builder()
                .documentType(documentType)
                .fileName(fileName)
                .status("DOCUMENT_UPLOADED")
                .build();
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {

            throw new RuntimeException(
                    "File is required"
            );
        }
    }

    private void validateDocumentType(
            String documentType) {

        if (documentType == null ||
                documentType.isBlank()) {

            throw new RuntimeException(
                    "Document type is required"
            );
        }

        List<String> allowedDocumentTypes =
                Arrays.asList(
                        "AADHAR_CARD",
                        "PAN_CARD",
                        "PASSPORT",
                        "DRIVING_LICENSE"
                );

        if (!allowedDocumentTypes.contains(
                documentType)) {

            throw new RuntimeException(
                    "Invalid document type"
            );
        }
    }

    private void validateContentType(
            MultipartFile file) {

        String contentType =
                file.getContentType();

        if (contentType == null ||
                !(contentType.equals("image/jpeg")
                        || contentType.equals("image/png"))) {

            throw new RuntimeException(
                    "Only JPG and PNG allowed"
            );
        }
    }

    private void validateFileSize(
            MultipartFile file) {

        long maxFileSize = 500 * 1024;

        if (file.getSize() > maxFileSize) {

            throw new RuntimeException(
                    "Max size 500KB"
            );
        }
    }
}