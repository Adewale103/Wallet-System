package com.threeline.wallet.service;

import com.threeline.wallet.dto.CreateUserRequest;
import com.threeline.wallet.dto.UserResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.exception.DuplicateResourceException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createUserAndAccount_createsUser_thenDelegatesWalletCreationToWalletService() {
        CreateUserRequest request = new CreateUserRequest("Alice", "Okafor", "alice@example.com");
        User savedUser = User.builder().id(1L).firstName("Alice").lastName("Okafor").email("alice@example.com").build();
        Wallet wallet = Wallet.builder().id(10L).accountNumber("1000000001").balance(BigDecimal.ZERO).user(savedUser).build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(walletService.createWalletFor(savedUser)).thenReturn(wallet);

        UserResponse response = userService.createUserAndAccount(request);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAccountNumber()).isEqualTo("1000000001");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletService).createWalletFor(savedUser);
    }

    @Test
    void createUserAndAccount_rejectsDuplicateEmail_withoutCallingWalletService() {
        CreateUserRequest request = new CreateUserRequest("Alice", "Okafor", "alice@example.com");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUserAndAccount(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
        verify(walletService, never()).createWalletFor(any());
    }

    @Test
    void createUserAndAccount_normalizesEmailToLowercase() {
        CreateUserRequest request = new CreateUserRequest("Alice", "Okafor", "ALICE@EXAMPLE.COM");
        User savedUser = User.builder().id(1L).firstName("Alice").lastName("Okafor").email("alice@example.com").build();
        Wallet wallet = Wallet.builder().id(10L).accountNumber("1000000001").balance(BigDecimal.ZERO).user(savedUser).build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(walletService.createWalletFor(savedUser)).thenReturn(wallet);

        userService.createUserAndAccount(request);

        verify(userRepository).existsByEmail("alice@example.com");
    }
}
