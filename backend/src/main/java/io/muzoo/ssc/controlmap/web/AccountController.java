package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.PasswordChangeRequest;
import io.muzoo.ssc.controlmap.web.dto.ProfileUpdateRequest;
import io.muzoo.ssc.controlmap.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-settings API (#5), scoped to the signed-in user via the {@link Principal} — a user can only
 * ever change their own profile/password. Authenticated (enforced by the security filter chain).
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request, Principal principal) {
        return accountService.updateProfile(principal.getName(), request.name());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, Principal principal) {
        accountService.changePassword(principal.getName(), request.currentPassword(), request.newPassword());
    }
}
