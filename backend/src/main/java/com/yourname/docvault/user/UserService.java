package com.yourname.docvault.user;

import com.yourname.docvault.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public User setAiAccess(Long userId, boolean aiAccess) {
        User user = requireUser(userId);
        user.setAiAccess(aiAccess);
        return userRepository.save(user);
    }
}
