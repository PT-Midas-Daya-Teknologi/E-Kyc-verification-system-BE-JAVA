package com.kyc.kyc_verification_system.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kyc.kyc_verification_system.entity.KycDocument;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long>{

    Optional<KycDocument> findBySessionId(String sessionId);
    
    Optional<KycDocument> findByUsername(
            String username);

	
}
