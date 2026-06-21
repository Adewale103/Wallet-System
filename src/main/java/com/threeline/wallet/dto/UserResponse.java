package com.threeline.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String accountNumber;
    private BigDecimal balance;
}
