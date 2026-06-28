package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account management for the signed-in user (#5): edit display name and change password.
 * Email and role are org-managed and not editable here. Passwords are re-hashed with BCrypt; the
 * current password is always re-verified before a change (the session being valid is not enough).
 */
@Service
@Transactional
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse updateProfile(String email, String name) {
        User user = currentUser(email);
        user.setName(name.trim());
        return UserResponse.from(users.save(user));
    }

    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = currentUser(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
    }

    private User currentUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Authenticated user not found."));
    }
}
