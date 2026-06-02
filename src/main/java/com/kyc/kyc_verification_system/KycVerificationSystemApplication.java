package com.kyc.kyc_verification_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KycVerificationSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(KycVerificationSystemApplication.class, args);
	}

}
