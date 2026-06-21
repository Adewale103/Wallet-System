package com.threeline.wallet.service;

import com.threeline.wallet.dto.CreateUserRequest;
import com.threeline.wallet.dto.UserResponse;

public interface UserService {

    UserResponse createUserAndAccount(CreateUserRequest request);
}
