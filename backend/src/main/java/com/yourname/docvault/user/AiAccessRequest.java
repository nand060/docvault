package com.yourname.docvault.user;

import jakarta.validation.constraints.NotNull;

public record AiAccessRequest(@NotNull Boolean aiAccess) {
}
