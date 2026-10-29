package com.devarena.auth;

import com.devarena.auth.AuthDtos.LoginRequest;
import com.devarena.auth.AuthDtos.RegisterRequest;
import com.devarena.auth.AuthDtos.WsTicketResponse;
import com.devarena.common.ApiException;
import com.devarena.users.User;
import com.devarena.users.UserDtos.MeResponse;
import com.devarena.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final LoginThrottle throttle;
    private final WsTicketService tickets;

    public AuthController(AuthService authService, UserService userService, AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository, LoginThrottle throttle, WsTicketService tickets) {
        this.authService = authService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.throttle = throttle;
        this.tickets = tickets;
    }

    /** Touching this endpoint makes Spring Security issue the XSRF-TOKEN cookie. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest req,
                                               HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        ensureNotLocked(req.username(), ip);
        User user = authService.register(req);
        signIn(AppUserPrincipal.from(user), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.me(user.getId()));
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                            HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        ensureNotLocked(req.login(), ip);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.login().trim(), req.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(req.login(), ip);
            // Same message whether the account exists or not.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Incorrect username or password");
        }
        throttle.recordSuccess(req.login());
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        signIn(principal, request, response);
        return userService.me(principal.id());
    }

    @PostMapping("/ws-ticket")
    public WsTicketResponse wsTicket(@AuthenticationPrincipal AppUserPrincipal me) {
        return new WsTicketResponse(tickets.issue(me), tickets.ttlSeconds());
    }

    private void ensureNotLocked(String login, String ip) {
        long wait = throttle.lockedForSeconds(login, ip);
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again in " + Math.max(1, wait / 60) + " minute(s).");
        }
    }

    private void signIn(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal stored = principal.withoutPassword();
        var token = UsernamePasswordAuthenticationToken.authenticated(stored, null, stored.getAuthorities());
        // New session id on login defeats session fixation.
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
