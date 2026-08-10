package com.start.overflow.identity.service;

import com.start.overflow.identity.dto.AuthResponse;
import com.start.overflow.identity.dto.LoginRequest;
import com.start.overflow.identity.dto.RegisterRequest;
import com.start.overflow.identity.dto.UserResponse;
import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.mapper.UserMapper;
import com.start.overflow.identity.security.IssuedToken;
import com.start.overflow.identity.security.JwtTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, UserService userService,
                       UserMapper userMapper, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userMapper = userMapper;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserResponse registered = userService.register(request);
        return login(new LoginRequest(registered.email(), request.password()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        AppUser user = userService.findByEmailOrThrow(request.email());
        IssuedToken token = jwtTokenService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), userMapper.toResponse(user));
    }
}
