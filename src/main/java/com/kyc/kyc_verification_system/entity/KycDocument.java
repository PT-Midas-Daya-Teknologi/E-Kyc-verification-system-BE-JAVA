package com.kyc.kyc_verification_system.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kyc_documents")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    private String documentType;

    private String fileName;

    private String fileType;
    
    private String username;
    
    @Column(columnDefinition = "TEXT")
    private String fileData;

    private String status;

    private LocalDateTime createdAt;
    
    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;
}