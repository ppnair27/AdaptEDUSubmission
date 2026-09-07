package com.example.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import procrastination_alg.SupabaseService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AuthController {

    private final SupabaseService supabaseService;

    @Autowired
    public AuthController(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    /**
     * User registration endpoint.
     * Triggers verification email from Supabase Auth and stages the user in the public users table.
     */
    @PostMapping("/signup")
    public Map<String, Object> signUp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");
        String username = payload.get("username");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Map.of("status", "error", "message", "Email and password are required.");
        }
        if (username == null || username.isBlank()) {
            username = email.split("@")[0];
        }
        return supabaseService.signUpAuth(email.trim().toLowerCase(), password, username.trim());
    }

    /**
     * User login endpoint.
     * Authenticates with Supabase Auth or public.users and returns the user_id.
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Map.of("status", "error", "message", "Email and password are required.");
        }
        return supabaseService.loginAuth(email.trim().toLowerCase(), password);
    }

    /**
     * Resend verification email endpoint.
     */
    @PostMapping("/resend")
    public Map<String, Object> resend(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isBlank()) {
            return Map.of("status", "error", "message", "Email is required.");
        }
        return supabaseService.resendVerification(email.trim().toLowerCase());
    }

    /**
     * Returns the Supabase Google OAuth authorization URL.
     */
    @GetMapping("/oauth/google-url")
    public Map<String, String> getGoogleOAuthUrl(@RequestParam(required = false) String redirectTo) {
        return Map.of("url", supabaseService.getGoogleOAuthUrl(redirectTo));
    }

    /**
     * Exchanges Supabase access token for user info, username, and user_id.
     */
    @PostMapping("/oauth-callback")
    public Map<String, Object> oauthCallback(@RequestBody Map<String, String> payload) {
        String accessToken = payload.get("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of("status", "error", "message", "Access token is required.");
        }
        return supabaseService.processOAuthUser(accessToken.trim());
    }
}

