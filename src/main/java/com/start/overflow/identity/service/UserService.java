package com.start.overflow.identity.service;

import com.start.overflow.identity.dto.RegisterRequest;
import com.start.overflow.identity.dto.UserResponse;
import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.mapper.UserMapper;
import com.start.overflow.identity.repository.UserRepository;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Já existe um usuário com esse e-mail");
        }
        String document = AppUser.normalizeDocument(request.document());
        if (userRepository.existsByDocument(document)) {
            throw new BusinessRuleException("Já existe um usuário com esse CPF ou CNPJ");
        }
        AppUser user = new AppUser(request.name(), email, document,
                passwordEncoder.encode(request.password()), UserRole.CUSTOMER);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        return userMapper.toResponse(currentUserEntity());
    }

    @Transactional(readOnly = true)
    public AppUser currentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("Usuário autenticado não encontrado");
        }
        try {
            return userRepository.findById(Long.valueOf(authentication.getName()))
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        } catch (NumberFormatException exception) {
            return findByEmailOrThrow(authentication.getName());
        }
    }

    @Transactional(readOnly = true)
    public AppUser findByEmailOrThrow(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional
    public void createAdminIfMissing(String name, String email, String rawPassword) {
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            userRepository.save(new AppUser(name, email,
                    passwordEncoder.encode(rawPassword), UserRole.ADMIN));
        }
    }
}
