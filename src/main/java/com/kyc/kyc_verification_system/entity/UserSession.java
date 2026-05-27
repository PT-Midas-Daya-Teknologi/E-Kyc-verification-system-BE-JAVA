package com.kyc.kyc_verification_system.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.*;
import lombok.Data;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_session")
@Data
public class UserSession {

    @Id
    private String id;

    private Long userId;

    private Long documentId;

    private Long videoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, Object>> attempts;

    private LocalDateTime sessionExpiry;

    private Boolean isActive;
}