package com.kyc.kyc_verification_system.repository;

import com.kyc.kyc_verification_system.entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID>{

    Optional<UserDocument> findBySessionId(UUID sessionId);
}
