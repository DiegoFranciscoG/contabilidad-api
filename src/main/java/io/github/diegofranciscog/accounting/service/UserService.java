package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.AppUser;
import io.github.diegofranciscog.accounting.domain.UserRole;
import io.github.diegofranciscog.accounting.dto.CreateUserRequest;
import io.github.diegofranciscog.accounting.dto.LoginRequest;
import io.github.diegofranciscog.accounting.dto.TokenResponse;
import io.github.diegofranciscog.accounting.dto.UserResponse;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.exception.TooManyRequestsException;
import io.github.diegofranciscog.accounting.mapper.AccountingMapper;
import io.github.diegofranciscog.accounting.repository.AppUserRepository;
import io.github.diegofranciscog.accounting.security.JwtService;
import io.github.diegofranciscog.accounting.security.LoginRateLimiter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final AccountingMapper mapper;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       LoginRateLimiter rateLimiter, JwtService jwtService, AccountingMapper mapper, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Wrong e-mail, wrong password and disabled user all get the same 401 (no user enumeration). */
    public TokenResponse login(LoginRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire(clientIp, request.email())) {
            throw new TooManyRequestsException("Demasiados intentos; espera un minuto");
        }
        String email = AppUser.normalizeEmail(request.email());
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        AppUser user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("Usuario", email));
        return new TokenResponse(jwtService.issue(user.getEmail(), user.getRole()), "Bearer", jwtService.ttlSeconds(),
                user.getRole());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByEmail().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return mapper.toResponse(createUser(request.email(), request.password(), request.fullName(), request.role()));
    }

    @Transactional
    public UserResponse changeEnabled(long id, boolean enabled) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
        user.changeEnabled(enabled);
        return mapper.toResponse(user);
    }

    /** Creates the user unless the e-mail already exists; used by the bootstrap of the first users. */
    @Transactional
    public boolean createIfMissing(String email, String password, String fullName, UserRole role) {
        if (users.existsByEmail(AppUser.normalizeEmail(email))) {
            return false;
        }
        createUser(email, password, fullName, role);
        return true;
    }

    private AppUser createUser(String email, String password, String fullName, UserRole role) {
        String normalized = AppUser.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        return users.save(new AppUser(normalized, passwordEncoder.encode(password), fullName, role, clock.instant()));
    }
}
