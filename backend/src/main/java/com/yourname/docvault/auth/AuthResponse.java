package com.yourname.docvault.auth;

public record AuthResponse(String token, Long userId, String username, boolean aiAccess) {
}
