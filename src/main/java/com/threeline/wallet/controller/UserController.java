package com.threeline.wallet.controller;

import com.threeline.wallet.dto.CreateUserRequest;
import com.threeline.wallet.dto.UserResponse;
import com.threeline.wallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Creates a user and provisions a wallet (account) for them in one step.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUserAndAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
