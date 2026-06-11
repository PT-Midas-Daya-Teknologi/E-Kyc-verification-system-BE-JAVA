package com.kyc.kyc_verification_system.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_id_seq_gen")
	@SequenceGenerator(name="users_id_seq_gen", sequenceName = "users_id_seq", allocationSize = 1)
	private Long id;
    
    @Column(unique = true)
    private String username;   

    private String name;     

}