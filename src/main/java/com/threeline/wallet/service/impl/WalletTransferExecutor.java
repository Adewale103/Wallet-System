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
import com.threeline.wallet.utils.TransferUtils;
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

    private final TransferUtils transferUtils;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public TransferResponse execute(TransferRequest request) {
        Wallet fromWalletLookup = transferUtils.resolveWallet(request.getFromIdentifier());
        Wallet toWalletLookup = transferUtils.resolveWallet(request.getToIdentifier());

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

        String reference = transferUtils.generateReference();
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

}
