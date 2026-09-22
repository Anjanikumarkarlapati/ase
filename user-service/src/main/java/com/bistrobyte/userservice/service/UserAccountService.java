package com.bistrobyte.userservice.service;

import com.bistrobyte.common.exception.BusinessRuleException;
import com.bistrobyte.common.exception.DuplicateResourceException;
import com.bistrobyte.common.exception.ForbiddenOperationException;
import com.bistrobyte.common.exception.ResourceNotFoundException;
import com.bistrobyte.common.security.AuthenticatedUser;
import com.bistrobyte.common.security.JwtService;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.userservice.domain.UserAccount;
import com.bistrobyte.userservice.dto.AuthResponse;
import com.bistrobyte.userservice.dto.ChangePasswordRequest;
import com.bistrobyte.userservice.dto.LoginRequest;
import com.bistrobyte.userservice.dto.RegistrationRequest;
import com.bistrobyte.userservice.dto.UpdateProfileRequest;
import com.bistrobyte.userservice.dto.UserResponse;
import com.bistrobyte.userservice.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Account lifecycle and credential verification. */
@Service
@Transactional(readOnly = true)
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserAccountService(UserAccountRepository repository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates an account. Only an authenticated ADMIN may request a role other than
     * CUSTOMER; anonymous sign-ups are always customers.
     */
    @Transactional
    public UserResponse register(RegistrationRequest request, boolean privilegedCaller) {
        if (repository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        Role requestedRole = Role.CUSTOMER;
        if (request.role() != null && !request.role().isBlank()) {
            if (!privilegedCaller) {
                throw new ForbiddenOperationException(
                        "Only an administrator may create an account with an elevated role");
            }
            requestedRole = Role.from(request.role());
        }

        UserAccount account = new UserAccount();
        account.setUsername(request.username().trim());
        account.setEmail(request.email().trim().toLowerCase());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setFullName(request.fullName().trim());
        account.setPhoneNumber(blankToNull(request.phoneNumber()));
        account.setRole(requestedRole);
        account.setActive(true);

        UserAccount saved = repository.save(account);
        log.info("Registered account id={} username={} role={}", saved.getId(), saved.getUsername(), saved.getRole());
        return UserResponse.from(saved);
    }

    /** Verifies credentials and mints an access token. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();
        UserAccount account = repository.findByUsernameIgnoreCase(identifier)
                .or(() -> repository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new BadCredentialsException("Invalid username/email or password"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            log.warn("Failed login attempt for identifier={}", identifier);
            throw new BadCredentialsException("Invalid username/email or password");
        }
        if (!account.isActive()) {
            throw new BusinessRuleException("This account has been deactivated. Contact an administrator.");
        }

        String token = jwtService.generateToken(
                account.getId(), account.getUsername(), account.getEmail(), account.getRole());
        log.info("Issued token for username={} role={}", account.getUsername(), account.getRole());
        return AuthResponse.of(token, jwtService.getExpirationMs() / 1000, UserResponse.from(account));
    }

    public UserResponse getById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    public UserResponse getByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for username " + username));
    }

    public Page<UserResponse> list(Role role, Pageable pageable) {
        Page<UserAccount> page = (role == null)
                ? repository.findAll(pageable)
                : repository.findByRole(role, pageable);
        return page.map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest request, AuthenticatedUser caller) {
        UserAccount account = findOrThrow(id);
        assertSelfOrAdmin(account, caller);

        String email = request.email().trim().toLowerCase();
        Optional<UserAccount> emailOwner = repository.findByEmailIgnoreCase(email);
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(account.getId())) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        account.setFullName(request.fullName().trim());
        account.setEmail(email);
        account.setPhoneNumber(blankToNull(request.phoneNumber()));
        return UserResponse.from(repository.save(account));
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request, AuthenticatedUser caller) {
        UserAccount account = findOrThrow(id);
        assertSelfOrAdmin(account, caller);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadCredentialsException("The current password is incorrect");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        repository.save(account);
        log.info("Password changed for account id={}", id);
    }

    @Transactional
    public UserResponse updateRole(Long id, String role) {
        UserAccount account = findOrThrow(id);
        account.setRole(Role.from(role));
        return UserResponse.from(repository.save(account));
    }

    /** Soft delete: the account is deactivated so historical orders keep a valid owner. */
    @Transactional
    public UserResponse setActive(Long id, boolean active) {
        UserAccount account = findOrThrow(id);
        account.setActive(active);
        return UserResponse.from(repository.save(account));
    }

    private UserAccount findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User account", id));
    }

    private void assertSelfOrAdmin(UserAccount account, AuthenticatedUser caller) {
        if (caller.role() == Role.ADMIN) {
            return;
        }
        if (!account.getId().equals(caller.userId())) {
            throw new ForbiddenOperationException("You may only modify your own account");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
