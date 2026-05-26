package com.kyc.kyc_verification_system.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.kyc.kyc_verification_system.dto.DocUploadResponse;
import com.kyc.kyc_verification_system.dto.InitiateResponse;
import com.kyc.kyc_verification_system.entity.KycDocument;
import com.kyc.kyc_verification_system.entity.User;
import com.kyc.kyc_verification_system.repository.KycDocumentRepository;
import com.kyc.kyc_verification_system.repository.UserRepository;
import com.kyc.kyc_verification_system.util.JwtUtil;

@Service
public class KycService {

    private final JwtUtil jwtUtil;

    private final KycDocumentRepository repository;

    private final UserRepository userRepository;

    public KycService(
            JwtUtil jwtUtil,
            KycDocumentRepository repository,
            UserRepository userRepository) {

        this.jwtUtil = jwtUtil;

        this.repository = repository;

        this.userRepository = userRepository;
    }

    public InitiateResponse initiateKyc(
            String username) {

        if (username == null
                || username.isBlank()) {

            throw new RuntimeException(
                    "Username is required"
            );
        }

        User user =
                userRepository.findByUsername(username)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Invalid username"
                                )
                        );

        

        if (user.getSessionId() != null
                && !user.getSessionId().isBlank()) {

            String token =
                    jwtUtil.generateToken(
                            user.getSessionId()
                    );

            return InitiateResponse.builder()
                    .token(token)
                    .expiresAt(
                            LocalDateTime.now()
                                    .plusMinutes(10)
                                    .toString()
                    )
                    .build();
        }

        

        String sessionId =
                "" + UUID.randomUUID();

        

        user.setSessionId(sessionId);

        userRepository.save(user);

        
        String token =
                jwtUtil.generateToken(sessionId);

        

        KycDocument document =
                new KycDocument();

        document.setUsername(username);

        document.setSessionId(sessionId);

        document.setStatus("KYC_INITIATED");

        document.setCreatedAt(
                LocalDateTime.now()
        );

        document.setCreatedBy("SYSTEM");

        repository.save(document);

        LocalDateTime expiresAt =
                LocalDateTime.now().plusMinutes(10);

        return InitiateResponse.builder()
                .token(token)
                .expiresAt(expiresAt.toString())
                .build();
    }

    public DocUploadResponse uploadDocument(

            String sessionId,
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

        String base64File =
                Base64.getEncoder()
                        .encodeToString(
                                file.getBytes()
                        );

        KycDocument document =
                repository.findBySessionId(sessionId)
                        .orElse(new KycDocument());

        boolean isNewDocument =
                document.getId() == null;

        document.setSessionId(sessionId);

        document.setDocumentType(documentType);

        document.setFileName(fileName);

        document.setFileType(contentType);

        document.setFileData(base64File);

        document.setStatus("DOCUMENT_UPLOADED");

        if (isNewDocument) {

            document.setCreatedAt(
                    LocalDateTime.now()
            );

            document.setCreatedBy("SYSTEM");
        }

        document.setUpdatedAt(
                LocalDateTime.now()
        );

        document.setUpdatedBy("SYSTEM");

        repository.save(document);

        return DocUploadResponse.builder()
                .documentType(documentType)
                .fileName(fileName)
                .status("DOCUMENT_UPLOADED")
                .build();
    }

    private void validateFile(
            MultipartFile file) {

        if (file == null
                || file.isEmpty()) {

            throw new RuntimeException(
                    "File is required"
            );
        }
    }

    private void validateDocumentType(
            String documentType) {

        if (documentType == null
                || documentType.isBlank()) {

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

        if (!allowedDocumentTypes
                .contains(documentType)) {

            throw new RuntimeException(
                    "Invalid document type"
            );
        }
    }

    private void validateContentType(
            MultipartFile file) {

        String contentType =
                file.getContentType();

        if (contentType == null
                || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png"))) {

            throw new RuntimeException(
                    "Only JPG and PNG image files are allowed"
            );
        }
    }

    private void validateFileSize(
            MultipartFile file) {

        long maxFileSize = 500 * 1024;

        if (file.getSize() > maxFileSize) {

            throw new RuntimeException(
                    "Maximum allowed image size is 500 KB"
            );
        }
    }
}