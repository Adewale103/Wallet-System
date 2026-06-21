package com.threeline.wallet.service;

import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.exception.InsufficientBalanceException;
import com.threeline.wallet.exception.InvalidTransferException;
import com.threeline.wallet.exception.ResourceNotFoundException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.repository.WalletRepository;
import com.threeline.wallet.repository.WalletTransactionRepository;
import com.threeline.wallet.service.impl.WalletTransferExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WalletTransferExecutorTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private WalletTransferExecutor executor;

    private User aliceUser;
    private Wallet aliceWallet;
    private User bobUser;
    private Wallet bobWallet;

    @BeforeEach
    void setUp() {
        aliceUser = User.builder().id(1L).firstName("Alice").lastName("Okafor").email("alice@example.com").build();
        aliceWallet = Wallet.builder().id(10L).accountNumber("1000000001").balance(new BigDecimal("500.00")).user(aliceUser).build();

        bobUser = User.builder().id(2L).firstName("Bob").lastName("Eze").email("bob@example.com").build();
        bobWallet = Wallet.builder().id(11L).accountNumber("1000000002").balance(new BigDecimal("100.00")).user(bobUser).build();
    }

    @Test
    void execute_movesFundsBetweenAccounts_andWritesTwoLedgerEntries() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("200.00"), "Rent");

        when(walletRepository.findByAccountNumber("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumber("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = executor.execute(request);

        assertThat(response.getFromAccountBalanceAfter()).isEqualByComparingTo("300.00");
        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("300.00");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("300.00");
        assertThat(response.getReference()).startsWith("TRX-");
        verify(walletTransactionRepository, times(2)).save(any());
    }

    @Test
    void execute_insufficientBalance_throwsAndDoesNotMutateBalances() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("10000.00"), null);

        when(walletRepository.findByAccountNumber("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumber("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000002")).thenReturn(Optional.of(bobWallet));

        assertThatThrownBy(() -> executor.execute(request))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("500.00");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("100.00");
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void execute_toSameAccount_isRejected() {
        TransferRequest request = new TransferRequest("1000000001", "1000000001", new BigDecimal("10.00"), null);
        when(walletRepository.findByAccountNumber("1000000001")).thenReturn(Optional.of(aliceWallet));

        assertThatThrownBy(() -> executor.execute(request))
                .isInstanceOf(InvalidTransferException.class);

        verify(walletRepository, never()).findByAccountNumberForUpdate(anyString());
    }

    @Test
    void execute_unknownSourceAccount_throwsNotFound() {
        TransferRequest request = new TransferRequest("0000000000", "1000000002", new BigDecimal("10.00"), null);
        when(walletRepository.findByAccountNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void execute_byEmailIdentifiers_resolvesBothWallets() {
        TransferRequest request = new TransferRequest("alice@example.com", "bob@example.com", new BigDecimal("75.00"), null);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceUser));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bobUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(bobWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = executor.execute(request);

        assertThat(response.getFromAccount()).isEqualTo("1000000001");
        assertThat(response.getToAccount()).isEqualTo("1000000002");
    }

    @Test
    void execute_exactBalance_succeedsAndZerosOutSourceWallet() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("500.00"), null);

        when(walletRepository.findByAccountNumber("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumber("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000002")).thenReturn(Optional.of(bobWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = executor.execute(request);

        assertThat(response.getFromAccountBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
