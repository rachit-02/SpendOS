package com.spendos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "Invalid email format") @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 255) String fullName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
