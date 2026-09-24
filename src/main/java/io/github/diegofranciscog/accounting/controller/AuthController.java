package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.LoginRequest;
import io.github.diegofranciscog.accounting.dto.TokenResponse;
import io.github.diegofranciscog.accounting.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final UserService service;

    public AuthController(UserService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Devuelve un JWT de corta duración (máximo 5 intentos por minuto por IP y por correo)")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return service.login(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario y rol del token actual")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of("email", authentication.getName(),
                "roles", authentication.getAuthorities().stream().map(Object::toString).toList());
    }
}
