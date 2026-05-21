package com.yourname.docvault.user;

public record UserResponse(Long id, String username, boolean aiAccess) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.isAiAccess());
    }
}
