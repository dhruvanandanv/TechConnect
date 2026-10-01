package com.techconnect.service.impl;

import com.techconnect.dto.AuthLoginRequest;
import com.techconnect.dto.AuthRegisterRequest;
import com.techconnect.dto.AuthResponse;
import com.techconnect.entity.Role;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.exception.DuplicateEmailException;
import com.techconnect.exception.InvalidCredentialsException;
import com.techconnect.exception.ResourceNotFoundException;
import com.techconnect.exception.UnauthorizedRoleAssignmentException;
import com.techconnect.mapper.UserMapper;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.AuthService;
import com.techconnect.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(AuthRegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // 1. Verify email uniqueness
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email address '" + normalizedEmail + "' is already registered");
        }

        // 2. Parse name into first and last name
        String trimmedName = request.getName().trim();
        String firstName;
        String lastName;
        int firstSpace = trimmedName.indexOf(' ');
        if (firstSpace > 0) {
            firstName = trimmedName.substring(0, firstSpace).trim();
            lastName = trimmedName.substring(firstSpace + 1).trim();
        } else {
            firstName = trimmedName;
            lastName = "";
        }

        // 3. Role determination and privilege escalation protection
        RoleName targetRoleName = determineRegistrationRole(request.getRole());
        Role role = roleRepository.findByName(targetRoleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role " + targetRoleName + " is not configured in system"));

        // 4. Secure password hashing with BCrypt
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // 5. Build and persist user entity
        User user = User.builder()
                .email(normalizedEmail)
                .password(hashedPassword)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered new user with ID: {} and role: {}", savedUser.getId(), targetRoleName);

        // 6. Return safe response without password or hash
        return AuthResponse.builder()
                .success(true)
                .message("User registered successfully")
                .user(userMapper.toUserResponse(savedUser))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(AuthLoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // 1. Lookup user by email
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // 2. Check active status
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("Account has been deactivated. Please contact support.");
        }

        // 3. Verify BCrypt password hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("Successful login for user ID: {} ({})", user.getId(), normalizedEmail);

        // 4. Generate JWT access token with role authority claim
        String roleAuthority = user.getRole().getName().name();
        String token = jwtService.generateToken(user.getEmail(), List.of(roleAuthority));

        // 5. Return safe response with JWT, bearer type, expiration, and user details
        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .user(userMapper.toUserResponse(user))
                .build();
    }

    private RoleName determineRegistrationRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return RoleName.ROLE_EMPLOYEE;
        }

        String normalizedRole = requestedRole.trim().toUpperCase();

        // Prevent privilege escalation via public registration
        if (normalizedRole.equals("ROLE_ADMIN") || normalizedRole.equals("ADMIN") ||
            normalizedRole.equals("ROLE_MANAGER") || normalizedRole.equals("MANAGER")) {
            throw new UnauthorizedRoleAssignmentException(
                    "Public registration cannot assign administrative or managerial roles: " + requestedRole);
        }

        if (normalizedRole.equals("ROLE_EMPLOYEE") || normalizedRole.equals("EMPLOYEE")) {
            return RoleName.ROLE_EMPLOYEE;
        }

        throw new UnauthorizedRoleAssignmentException("Invalid or unsupported role: " + requestedRole);
    }
}
