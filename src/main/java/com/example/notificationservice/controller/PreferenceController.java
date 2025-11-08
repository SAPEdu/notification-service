package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.dto.PreferenceDto;
import com.example.notificationservice.service.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final CasdoorAuthenticationContext authContext;

    /**
     * Get current user's preferences (authenticated user only)
     */
    @GetMapping("/preferences")
    public ResponseEntity<PreferenceDto> getMyPreferences() {
        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} fetching their own preferences", userId);
        return preferenceService.getUserPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update current user's preferences (authenticated user only)
     */
    @PutMapping("/preferences")
    public ResponseEntity<PreferenceDto> updateMyPreferences(
            @Valid @RequestBody PreferenceDto preferenceDto) {

        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} updating their own preferences", userId);

        // Force the userId from JWT token, ignore any userId in the request body
        preferenceDto.setUserId(userId);

        PreferenceDto updated = preferenceService.updateUserPreferences(preferenceDto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Create preferences for current user (authenticated user only)
     */
    @PostMapping("/preferences")
    public ResponseEntity<PreferenceDto> createMyPreferences(
            @Valid @RequestBody PreferenceDto preferenceDto) {

        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} creating their preferences", userId);

        // Force the userId from JWT token
        preferenceDto.setUserId(userId);

        PreferenceDto created = preferenceService.createUserPreferences(preferenceDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ==================== ADMIN ENDPOINTS ====================

    /**
     * Admin: Get any user's preferences
     */
    @GetMapping("/admin/users/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreferenceDto> getUserPreferencesAsAdmin(@PathVariable Integer userId) {
        log.info("Admin fetching preferences for user: {}", userId);
        return preferenceService.getUserPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Admin: Update any user's preferences
     */
    @PutMapping("/admin/users/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreferenceDto> updateUserPreferencesAsAdmin(
            @PathVariable Integer userId,
            @Valid @RequestBody PreferenceDto preferenceDto) {

        Integer adminId = authContext.getCurrentUserId().orElse(null);
        log.info("Admin {} updating preferences for user: {}", adminId, userId);

        // Admin can set any userId
        preferenceDto.setUserId(userId);
        PreferenceDto updated = preferenceService.updateUserPreferences(preferenceDto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Admin: Create preferences for any user
     */
    @PostMapping("/admin/users/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PreferenceDto> createUserPreferencesAsAdmin(
            @PathVariable Integer userId,
            @Valid @RequestBody PreferenceDto preferenceDto) {

        Integer adminId = authContext.getCurrentUserId().orElse(null);
        log.info("Admin {} creating preferences for user: {}", adminId, userId);

        preferenceDto.setUserId(userId);
        PreferenceDto created = preferenceService.createUserPreferences(preferenceDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Admin: Delete any user's preferences
     */
    @DeleteMapping("/admin/users/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUserPreferencesAsAdmin(@PathVariable Integer userId) {
        Integer adminId = authContext.getCurrentUserId().orElse(null);
        log.info("Admin {} deleting preferences for user: {}", adminId, userId);

        boolean deleted = preferenceService.deleteUserPreferences(userId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}