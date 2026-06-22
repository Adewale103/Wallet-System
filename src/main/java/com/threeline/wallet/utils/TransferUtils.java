package com.threeline.wallet.utils;

import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.exception.ResourceNotFoundException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferUtils {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public String generateReference() {
        return "TRX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public Wallet resolveWallet(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            User user = userRepository.findByEmail(trimmed.toLowerCase())
                    .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + trimmed));
            return walletRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No wallet found for user with email: " + trimmed));
        }
        return walletRepository.findByAccountNumber(trimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for account: " + trimmed));
    }
}
