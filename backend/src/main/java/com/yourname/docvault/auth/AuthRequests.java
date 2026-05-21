package com.yourname.docvault.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record RegisterRequest(
            @Email @NotBlank String username,
            @Size(min = 8, max = 100) String password
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
