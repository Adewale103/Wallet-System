package com.threeline.wallet.service.impl;

import com.threeline.wallet.config.AccountNumberGenerator;
import com.threeline.wallet.dto.FundWalletRequest;
import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.dto.WalletResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.entity.WalletTransaction;
import com.threeline.wallet.enums.TransactionStatus;
import com.threeline.wallet.enums.TransactionType;
import com.threeline.wallet.exception.ResourceNotFoundException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.repository.WalletRepository;
import com.threeline.wallet.repository.WalletTransactionRepository;
import com.threeline.wallet.service.WalletService;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final WalletTransferExecutor walletTransferExecutor;

    @Override
    @Transactional
    public Wallet createWalletFor(User user) {
        Wallet wallet = Wallet.builder()
                .accountNumber(generateUniqueAccountNumber())
                .balance(BigDecimal.ZERO)
                .user(user)
                .build();
        wallet = walletRepository.save(wallet);

        log.info("Opened wallet accountNumber={} for userId={}", wallet.getAccountNumber(), user.getId());
        return wallet;
    }

    @Override
    @Transactional
    public WalletResponse fundWallet(FundWalletRequest request) {
        Wallet wallet = lockWalletByIdentifier(request.getAccountIdentifier());

        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        walletTransactionRepository.save(WalletTransaction.builder()
                .reference(generateReference())
                .walletId(wallet.getId())
                .accountNumber(wallet.getAccountNumber())
                .type(TransactionType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(newBalance)
                .status(TransactionStatus.SUCCESSFUL)
                .narration(request.getNarration() != null ? request.getNarration() : "Wallet funding")
                .build());

        log.info("Funded wallet accountNumber={} amount={} newBalance={}",
                wallet.getAccountNumber(), request.getAmount(), newBalance);

        return toWalletResponse(wallet);
    }

    private static final int MAX_TRANSFER_ATTEMPTS = 10;

    @Override
    public TransferResponse transfer(TransferRequest request) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return walletTransferExecutor.execute(request);
            } catch (ConcurrencyFailureException | PersistenceException ex) {
                if (attempt >= MAX_TRANSFER_ATTEMPTS) {
                    log.warn("Transfer {} -> {} failed after {} attempts due to repeated concurrent conflicts",
                            request.getFromIdentifier(), request.getToIdentifier(), attempt);
                    throw ex;
                }
                log.debug("Transfer {} -> {} hit a concurrent update on attempt {}, retrying",
                        request.getFromIdentifier(), request.getToIdentifier(), attempt);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(String accountIdentifier) {
        return toWalletResponse(resolveWallet(accountIdentifier));
    }

    // ---- helpers ----

    private Wallet lockWalletByIdentifier(String identifier) {
        String accountNumber = resolveWallet(identifier).getAccountNumber();
        return walletRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + accountNumber));
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

    private String generateUniqueAccountNumber() {
        String accountNumber;
        int attempts = 0;
        do {
            accountNumber = accountNumberGenerator.generate();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Unable to generate a unique account number after 10 attempts");
            }
        } while (walletRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private String generateReference() {
        return "TRX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        User owner = wallet.getUser();
        return WalletResponse.builder()
                .accountNumber(wallet.getAccountNumber())
                .balance(wallet.getBalance())
                .ownerName(owner != null ? owner.getFirstName() + " " + owner.getLastName() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .build();
    }
}
