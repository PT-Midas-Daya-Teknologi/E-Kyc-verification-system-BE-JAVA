package com.kyc.kyc_verification_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kyc.kyc_verification_system.entity.UserDocument;

public interface UserDocumentRepository extends JpaRepository<UserDocument, Long>{

}
