package com.threeline.wallet.dto;

import com.threeline.wallet.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferResponse {
    private String reference;
    private TransactionStatus status;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private BigDecimal fromAccountBalanceAfter;
    private LocalDateTime processedAt;
}
