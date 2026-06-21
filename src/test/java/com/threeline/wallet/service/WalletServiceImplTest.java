package com.threeline.wallet.service;

import com.threeline.wallet.config.AccountNumberGenerator;
import com.threeline.wallet.dto.FundWalletRequest;
import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;
import com.threeline.wallet.exception.ResourceNotFoundException;
import com.threeline.wallet.repository.UserRepository;
import com.threeline.wallet.repository.WalletRepository;
import com.threeline.wallet.repository.WalletTransactionRepository;
import com.threeline.wallet.service.impl.WalletServiceImpl;
import com.threeline.wallet.service.impl.WalletTransferExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private WalletTransferExecutor walletTransferExecutor;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User aliceUser;
    private Wallet aliceWallet;

    @BeforeEach
    void setUp() {
        aliceUser = User.builder().id(1L).firstName("Alice").lastName("Okafor").email("alice@example.com").build();
        aliceWallet = Wallet.builder().id(10L).accountNumber("1000000001").balance(new BigDecimal("500.00")).user(aliceUser).build();
    }


    @Test
    void createWalletFor_opensWalletWithZeroBalance_forGivenUser() {
        when(accountNumberGenerator.generate()).thenReturn("1000000001");
        when(walletRepository.existsByAccountNumber("1000000001")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet wallet = walletService.createWalletFor(aliceUser);

        assertThat(wallet.getAccountNumber()).isEqualTo("1000000001");
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getUser()).isEqualTo(aliceUser);
    }

    @Test
    void createWalletFor_retriesGeneratorOnAccountNumberCollision() {
        when(accountNumberGenerator.generate()).thenReturn("1111111111", "2222222222");
        when(walletRepository.existsByAccountNumber("1111111111")).thenReturn(true);
        when(walletRepository.existsByAccountNumber("2222222222")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet wallet = walletService.createWalletFor(aliceUser);

        assertThat(wallet.getAccountNumber()).isEqualTo("2222222222");
    }


    @Test
    void fundWallet_increasesBalance_andRecordsCreditLedgerEntry() {
        FundWalletRequest request = new FundWalletRequest("1000000001", new BigDecimal("250.00"), "Top up");

        when(walletRepository.findByAccountNumber("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = walletService.fundWallet(request);

        assertThat(response.getBalance()).isEqualByComparingTo("750.00");
        verify(walletTransactionRepository, times(1)).save(any());
    }

    @Test
    void fundWallet_byEmail_resolvesCorrectWallet() {
        FundWalletRequest request = new FundWalletRequest("alice@example.com", new BigDecimal("50.00"), null);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(aliceUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByAccountNumberForUpdate("1000000001")).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = walletService.fundWallet(request);

        assertThat(response.getAccountNumber()).isEqualTo("1000000001");
        assertThat(response.getBalance()).isEqualByComparingTo("550.00");
    }

    @Test
    void fundWallet_unknownAccount_throwsNotFound() {
        FundWalletRequest request = new FundWalletRequest("9999999999", new BigDecimal("50.00"), null);
        when(walletRepository.findByAccountNumber("9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.fundWallet(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    @Test
    void transfer_delegatesToExecutor_andReturnsItsResultOnFirstTry() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("50.00"), null);
        TransferResponse expected = TransferResponse.builder().reference("TRX-ABC").build();
        when(walletTransferExecutor.execute(request)).thenReturn(expected);

        TransferResponse response = walletService.transfer(request);

        assertThat(response).isSameAs(expected);
        verify(walletTransferExecutor, times(1)).execute(request);
    }

    @Test
    void transfer_retriesOnOptimisticLockConflict_thenSucceeds() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("50.00"), null);
        TransferResponse expected = TransferResponse.builder().reference("TRX-ABC").build();

        when(walletTransferExecutor.execute(request))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, "10"))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, "10"))
                .thenReturn(expected);

        TransferResponse response = walletService.transfer(request);

        assertThat(response).isSameAs(expected);
        verify(walletTransferExecutor, times(3)).execute(request);
    }

    @Test
    void transfer_givesUpAfterMaxAttempts_andRethrows() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("50.00"), null);

        when(walletTransferExecutor.execute(request))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, "10"));

        assertThatThrownBy(() -> walletService.transfer(request))
                .isInstanceOf(ConcurrencyFailureException.class);

        // MAX_TRANSFER_ATTEMPTS = 5
        verify(walletTransferExecutor, times(10)).execute(request);
    }

    @Test
    void transfer_doesNotRetry_onNonConcurrencyException() {
        TransferRequest request = new TransferRequest("1000000001", "1000000002", new BigDecimal("50.00"), null);
        when(walletTransferExecutor.execute(request))
                .thenThrow(new ResourceNotFoundException("Wallet not found: 1000000001"));

        assertThatThrownBy(() -> walletService.transfer(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletTransferExecutor, times(1)).execute(request);
    }
}
