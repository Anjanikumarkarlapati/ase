package com.bistrobyte.userservice.controller;

import com.bistrobyte.common.dto.PageResponse;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.common.security.SecurityUtils;
import com.bistrobyte.userservice.dto.ChangePasswordRequest;
import com.bistrobyte.userservice.dto.UpdateProfileRequest;
import com.bistrobyte.userservice.dto.UpdateRoleRequest;
import com.bistrobyte.userservice.dto.UserResponse;
import com.bistrobyte.userservice.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Account administration and self-service profile management. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Account administration and profile management")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "List accounts, optionally filtered by role")
    public ResponseEntity<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "username") String sortBy) {
        Role roleFilter = (role == null || role.isBlank()) ? null : Role.from(role);
        Page<UserResponse> result = userAccountService.list(
                roleFilter, PageRequest.of(page, Math.min(size, 100), Sort.by(sortBy)));
        return ResponseEntity.ok(PageResponse.of(result, user -> user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF') or #id == authentication.principal.userId()")
    @Operation(summary = "Fetch a single account")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.getById(id));
    }

    @GetMapping("/by-username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','KITCHEN')")
    @Operation(summary = "Look up an account by username",
            description = "Used by the order service to enrich order tickets with customer details.")
    public ResponseEntity<UserResponse> getByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userAccountService.getByUsername(username));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a profile (self, or any profile for an ADMIN)")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                userAccountService.updateProfile(id, request, SecurityUtils.requireCurrentUser()));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Change a password")
    public ResponseEntity<Void> changePassword(@PathVariable Long id,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userAccountService.changePassword(id, request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Promote or demote an account")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(userAccountService.updateRole(id, request.role()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate an account",
            description = "A soft delete: the account can no longer log in but its order history stays intact.")
    public ResponseEntity<UserResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.setActive(id, false));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Re-activate a deactivated account")
    public ResponseEntity<UserResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountService.setActive(id, true));
    }
}
