package com.kyc.kyc_verification_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kyc.kyc_verification_system.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, String>  {

}
