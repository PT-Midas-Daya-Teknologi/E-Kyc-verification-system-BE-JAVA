package com.kyc.kyc_verification_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_session")
@Data
public class UserSession {

    @Id
    private UUID id;

    private Long  userId;

    private UUID documentId;

    private UUID videoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String attempts;

    private LocalDateTime sessionExpiry;

    private Boolean isActive;
}