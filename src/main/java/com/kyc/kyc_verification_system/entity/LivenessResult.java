package com.kyc.kyc_verification_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "liveness_results")
public class LivenessResult {
	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    private String sessionId;

	    private String status;

	    private Double confidence;

	    private Boolean live;

	    private LocalDateTime createdAt = LocalDateTime.now();


	    public Long getId() {
	        return id;
	    }

	    public void setId(Long id) {
	        this.id = id;
	    }

	    public String getSessionId() {
	        return sessionId;
	    }

	    public void setSessionId(String sessionId) {
	        this.sessionId = sessionId;
	    }

	    public String getStatus() {
	        return status;
	    }

	    public void setStatus(String status) {
	        this.status = status;
	    }

	    public Double getConfidence() {
	        return confidence;
	    }

	    public void setConfidence(Double confidence) {
	        this.confidence = confidence;
	    }

	    public Boolean getLive() {
	        return live;
	    }

	    public void setLive(Boolean live) {
	        this.live = live;
	    }

	    public LocalDateTime getCreatedAt() {
	        return createdAt;
	    }

	    public void setCreatedAt(LocalDateTime createdAt) {
	        this.createdAt = createdAt;
	    }
}