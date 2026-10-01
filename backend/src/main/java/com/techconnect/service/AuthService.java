package com.techconnect.service;

import com.techconnect.dto.AuthLoginRequest;
import com.techconnect.dto.AuthRegisterRequest;
import com.techconnect.dto.AuthResponse;

public interface AuthService {
    AuthResponse register(AuthRegisterRequest request);
    AuthResponse login(AuthLoginRequest request);
}
