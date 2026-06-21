package com.threeline.wallet.controller;

import com.threeline.wallet.dto.FundWalletRequest;
import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.dto.WalletResponse;
import com.threeline.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /** Deposits funds into a wallet. Added so transfers have a balance to work against in testing/demo. */
    @PostMapping("/fund")
    public ResponseEntity<WalletResponse> fundWallet(@Valid @RequestBody FundWalletRequest request) {
        return ResponseEntity.ok(walletService.fundWallet(request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(walletService.transfer(request));
    }

    /** Accepts either an account number or an email address as the path identifier. */
    @GetMapping("/{identifier}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String identifier) {
        return ResponseEntity.ok(walletService.getWallet(identifier));
    }
}
