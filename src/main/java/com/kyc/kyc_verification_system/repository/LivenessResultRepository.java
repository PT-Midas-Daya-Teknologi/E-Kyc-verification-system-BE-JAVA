package com.kyc.kyc_verification_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kyc.kyc_verification_system.entity.LivenessResult;
public interface LivenessResultRepository  extends  JpaRepository<LivenessResult, Long>{

}