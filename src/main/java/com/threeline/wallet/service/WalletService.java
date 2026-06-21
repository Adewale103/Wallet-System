package com.threeline.wallet.service;

import com.threeline.wallet.dto.FundWalletRequest;
import com.threeline.wallet.dto.TransferRequest;
import com.threeline.wallet.dto.TransferResponse;
import com.threeline.wallet.dto.WalletResponse;
import com.threeline.wallet.entity.User;
import com.threeline.wallet.entity.Wallet;


public interface WalletService {

    Wallet createWalletFor(User user);

    WalletResponse fundWallet(FundWalletRequest request);

    TransferResponse transfer(TransferRequest request);

    WalletResponse getWallet(String accountIdentifier);
}
