package com.bistrobyte.userservice.controller;

import com.bistrobyte.common.security.Role;
import com.bistrobyte.common.security.SecurityUtils;
import com.bistrobyte.userservice.dto.AuthResponse;
import com.bistrobyte.userservice.dto.LoginRequest;
import com.bistrobyte.userservice.dto.RegistrationRequest;
import com.bistrobyte.userservice.dto.UserResponse;
import com.bistrobyte.userservice.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication surface: sign-up, sign-in and "who am I". */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login and token issuance")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Anonymous sign-ups always create a CUSTOMER. An authenticated ADMIN may "
                    + "supply a role to provision STAFF, KITCHEN or ADMIN accounts.")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        boolean privileged = SecurityUtils.hasRole(Role.ADMIN);
        UserResponse created = userAccountService.register(request, privileged);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT access token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userAccountService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the profile behind the presented token")
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(userAccountService.getById(SecurityUtils.requireCurrentUser().userId()));
    }
}
