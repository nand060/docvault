package com.yourname.docvault.user;

import com.yourname.docvault.auth.CurrentUser;
import com.yourname.docvault.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser UserPrincipal principal) {
        return UserResponse.from(userService.requireUser(principal.id()));
    }

    @PutMapping("/ai-access")
    public UserResponse updateAiAccess(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody AiAccessRequest request
    ) {
        return UserResponse.from(userService.setAiAccess(principal.id(), request.aiAccess()));
    }
}
