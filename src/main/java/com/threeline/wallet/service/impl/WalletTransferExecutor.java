package com.threeline.wallet.service.impl;

import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.entity.WalletTransaction;
import com.threeline.wallet.enums.TransactionStatus;
import com.threeline.wallet.enums.TransactionType;
import com.threeline.wallet.exception.InsufficientBalanceException;
import com.threeline.wallet.exception.InvalidTransferException;
import com.threeline.wallet.exception.ResourceNotFoundException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.repository.WalletRepository;
import com.threeline.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class WalletTransferExecutor {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public TransferResponse execute(TransferRequest request) {
        Wallet fromWalletLookup = resolveWallet(request.getFromIdentifier());
        Wallet toWalletLookup = resolveWallet(request.getToIdentifier());

        if (fromWalletLookup.getAccountNumber().equals(toWalletLookup.getAccountNumber())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        String first = fromWalletLookup.getAccountNumber().compareTo(toWalletLookup.getAccountNumber()) < 0
                ? fromWalletLookup.getAccountNumber() : toWalletLookup.getAccountNumber();
        String second = first.equals(fromWalletLookup.getAccountNumber())
                ? toWalletLookup.getAccountNumber() : fromWalletLookup.getAccountNumber();

        Wallet firstLocked = walletRepository.findByAccountNumberForUpdate(first)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + first));
        Wallet secondLocked = walletRepository.findByAccountNumberForUpdate(second)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + second));

        Wallet fromWallet = fromWalletLookup.getAccountNumber().equals(first) ? firstLocked : secondLocked;
        Wallet toWallet = toWalletLookup.getAccountNumber().equals(first) ? firstLocked : secondLocked;

        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in account " + fromWallet.getAccountNumber());
        }

        BigDecimal fromNewBalance = fromWallet.getBalance().subtract(request.getAmount());
        BigDecimal toNewBalance = toWallet.getBalance().add(request.getAmount());

        fromWallet.setBalance(fromNewBalance);
        toWallet.setBalance(toNewBalance);
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        String reference = generateReference();
        String narration = request.getNarration() != null ? request.getNarration() : "Fund transfer";

        walletTransactionRepository.save(WalletTransaction.builder()
                .reference(reference)
                .walletId(fromWallet.getId())
                .accountNumber(fromWallet.getAccountNumber())
                .type(TransactionType.DEBIT)
                .amount(request.getAmount())
                .balanceAfter(fromNewBalance)
                .counterpartyAccountNumber(toWallet.getAccountNumber())
                .status(TransactionStatus.SUCCESSFUL)
                .narration(narration)
                .build());

        walletTransactionRepository.save(WalletTransaction.builder()
                .reference(reference)
                .walletId(toWallet.getId())
                .accountNumber(toWallet.getAccountNumber())
                .type(TransactionType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(toNewBalance)
                .counterpartyAccountNumber(fromWallet.getAccountNumber())
                .status(TransactionStatus.SUCCESSFUL)
                .narration(narration)
                .build());

        log.info("Transfer {} successful: {} -> {} amount={}",
                reference, fromWallet.getAccountNumber(), toWallet.getAccountNumber(), request.getAmount());

        return TransferResponse.builder()
                .reference(reference)
                .status(TransactionStatus.SUCCESSFUL)
                .fromAccount(fromWallet.getAccountNumber())
                .toAccount(toWallet.getAccountNumber())
                .amount(request.getAmount())
                .fromAccountBalanceAfter(fromNewBalance)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private Wallet resolveWallet(String identifier) {
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

    private String generateReference() {
        return "TRX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
