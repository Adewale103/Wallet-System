package com.threeline.wallet.service.impl;

import com.threeline.wallet.dto.CreateUserRequest;
import com.threeline.wallet.dto.UserResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.exception.DuplicateResourceException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.service.UserService;
import com.threeline.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public UserResponse createUserAndAccount(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user already exists with email: " + email);
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .build();
        user = userRepository.save(user);

        Wallet wallet = walletService.createWalletFor(user);

        log.info("Onboarded user id={} with wallet accountNumber={}", user.getId(), wallet.getAccountNumber());

        return UserResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .accountNumber(wallet.getAccountNumber())
                .balance(wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO)
                .build();
    }
}
