package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller to verify Casdoor JWT integration
 * Remove this controller in production!
 */
@RestController
@RequestMapping("/api/v1/auth/test")
@RequiredArgsConstructor
@Slf4j
public class AuthTestController {

    private final CasdoorAuthenticationContext authContext;

    /**
     * Test endpoint to verify JWT token is working
     * Returns all user information extracted from the token
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        log.info("Testing JWT token extraction...");

        // Debug log all claims
        authContext.debugLogClaims();

        Map<String, Object> userInfo = new HashMap<>();

        // Basic info
        userInfo.put("userId", authContext.getCurrentUserId().orElse(null));
        userInfo.put("username", authContext.getCurrentUsername().orElse("unknown"));
        userInfo.put("email", authContext.getCurrentUserEmail().orElse(null));
        userInfo.put("phone", authContext.getCurrentUserPhone().orElse(null));
        userInfo.put("organization", authContext.getCurrentOrganization().orElse(null));

        // Roles and permissions
        userInfo.put("roles", authContext.getCurrentUserRoles());
        userInfo.put("isAdmin", authContext.isAdmin());
        userInfo.put("isUser", authContext.isUser());

        // Authentication details
        Authentication auth = authContext.getAuthentication();
        if (auth != null) {
            userInfo.put("authenticated", auth.isAuthenticated());
            userInfo.put("principal", auth.getPrincipal().getClass().getSimpleName());
            userInfo.put("authorities", auth.getAuthorities().toString());
        }

        // All JWT claims (for debugging)
        userInfo.put("allClaims", authContext.getAllClaims().orElse(null));

        log.info("User info extracted: {}", userInfo);

        return ResponseEntity.ok(userInfo);
    }

    /**
     * Simple health check that requires authentication
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, String>> protectedEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "If you see this, your JWT token is valid!");
        response.put("user", authContext.getCurrentUsername().orElse("unknown"));
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return ResponseEntity.ok(response);
    }

    /**
     * Admin-only endpoint for testing role-based access
     */
    @GetMapping("/admin-only")
    public ResponseEntity<Map<String, String>> adminOnlyEndpoint() {
        if (!authContext.isAdmin()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Access Denied");
            error.put("message", "This endpoint requires admin role");
            error.put("yourRoles", authContext.getCurrentUserRoles().toString());
            return ResponseEntity.status(403).body(error);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Welcome Admin!");
        response.put("admin", authContext.getCurrentUsername().orElse("unknown"));

        return ResponseEntity.ok(response);
    }
}