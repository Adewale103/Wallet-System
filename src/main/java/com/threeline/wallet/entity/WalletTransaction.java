package com.threeline.wallet.entity;

import com.threeline.wallet.enums.TransactionStatus;
import com.threeline.wallet.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_tx_reference", columnList = "reference"),
        @Index(name = "idx_tx_wallet_id", columnList = "wallet_id")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletTransaction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(nullable = false, length = 10)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 10)
    private String counterpartyAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransactionStatus status;

    private String narration;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
