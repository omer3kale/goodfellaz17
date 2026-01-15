package com.goodfellaz17.application.dto.generated;

import jakarta.validation.constraints.*;

import java.io.Serializable;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * DTO: UserRegistrationRequest
 * 
 * Request payload for new user registration.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public record UserRegistrationRequest(
    
    @NotNull(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email cannot exceed 255 characters")
    String email,
    
    @NotNull(message = "password is required")
    @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
        message = "password must contain at least one lowercase, one uppercase, and one digit"
    )
    String password,
    
    @Size(max = 32, message = "referralCode cannot exceed 32 characters")
    String referralCode

) implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String email;
        private String password;
        private String referralCode;
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder referralCode(String referralCode) {
            this.referralCode = referralCode;
            return this;
        }
        
        public UserRegistrationRequest build() {
            return new UserRegistrationRequest(email, password, referralCode);
        }
    }
}
