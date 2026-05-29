package com.kyc.liveness.repository;


import com.kyc.liveness.entity.LivenessResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
public interface LivenessResultRepository  extends  JpaRepository<LivenessResult, Long>{

}
