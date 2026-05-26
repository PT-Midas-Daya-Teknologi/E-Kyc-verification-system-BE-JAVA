package com.kyc.kyc_verification_system.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kyc.kyc_verification_system.entity.User;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByUsername(
            String username);
}