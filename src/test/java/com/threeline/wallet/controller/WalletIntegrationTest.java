package com.threeline.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threeline.wallet.dto.CreateUserRequest;
import com.threeline.wallet.dto.FundWalletRequest;
import com.threeline.wallet.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createUserAndGetAccountNumber(String first, String last, String email) throws Exception {
        CreateUserRequest req = new CreateUserRequest(first, last, email);
        String body = mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accountNumber").asText();
    }

    @Test
    void createUser_then_fund_then_transfer_endToEndFlow() throws Exception {
        String aliceAcct = createUserAndGetAccountNumber("Adewale", "Ojo", "adewale.flow@example.com");
        String bobAcct = createUserAndGetAccountNumber("Chioma", "Bello", "chioma.flow@example.com");

        FundWalletRequest fundReq = new FundWalletRequest(aliceAcct, new BigDecimal("1000.00"), "Initial deposit");
        mockMvc.perform(post("/api/v1/wallets/fund")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(fundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.00));

        TransferRequest transferReq = new TransferRequest(aliceAcct, bobAcct, new BigDecimal("400.00"), "Lunch money");
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.fromAccountBalanceAfter").value(600.00));

        mockMvc.perform(get("/api/v1/wallets/" + aliceAcct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600.00));

        mockMvc.perform(get("/api/v1/wallets/" + bobAcct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400.00));
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        createUserAndGetAccountNumber("Tunde", "Fashola", "tunde.dup@example.com");

        CreateUserRequest dupReq = new CreateUserRequest("Tunde", "Fashola", "tunde.dup@example.com");
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest("Femi", "Adeyemi", "not-an-email");
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("email")));
    }

    @Test
    void transfer_insufficientFunds_returns422() throws Exception {
        String aliceAcct = createUserAndGetAccountNumber("Kemi", "Adams", "kemi.422@example.com");
        String bobAcct = createUserAndGetAccountNumber("Seun", "Bakare", "seun.422@example.com");

        TransferRequest req = new TransferRequest(aliceAcct, bobAcct, new BigDecimal("50.00"), null);
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Insufficient")));
    }

    @Test
    void transfer_unknownAccount_returns404() throws Exception {
        String aliceAcct = createUserAndGetAccountNumber("Yusuf", "Bello", "yusuf.404@example.com");

        TransferRequest req = new TransferRequest(aliceAcct, "0000000000", new BigDecimal("10.00"), null);
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_negativeAmount_returns400() throws Exception {
        String aliceAcct = createUserAndGetAccountNumber("Ngozi", "Eke", "ngozi.neg@example.com");
        String bobAcct = createUserAndGetAccountNumber("Ifeanyi", "Obi", "ifeanyi.neg@example.com");

        TransferRequest req = new TransferRequest(aliceAcct, bobAcct, new BigDecimal("-10.00"), null);
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_byEmail_works() throws Exception {
        String aliceAcct = createUserAndGetAccountNumber("Bisi", "Ade", "bisi.email@example.com");
        createUserAndGetAccountNumber("Dele", "Are", "dele.email@example.com");

        mockMvc.perform(post("/api/v1/wallets/fund")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                        new FundWalletRequest(aliceAcct, new BigDecimal("300.00"), null))))
                .andExpect(status().isOk());

        TransferRequest req = new TransferRequest("bisi.email@example.com", "dele.email@example.com", new BigDecimal("100.00"), null);
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    }

    @Test
    void concurrentTransfers_fromSameWallet_doNotCauseLostUpdates() throws Exception {
        String sourceAcct = createUserAndGetAccountNumber("Concurrent", "Source", "concurrent.source@example.com");
        String destAcct = createUserAndGetAccountNumber("Concurrent", "Dest", "concurrent.dest@example.com");

        mockMvc.perform(post("/api/v1/wallets/fund")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                        new FundWalletRequest(sourceAcct, new BigDecimal("1000.00"), null))))
                .andExpect(status().isOk());

        int concurrentTransfers = 10;
        BigDecimal amountEach = new BigDecimal("50.00");

        ExecutorService executor = Executors.newFixedThreadPool(concurrentTransfers);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < concurrentTransfers; i++) {
                futures.add(executor.submit(() -> {
                    TransferRequest req = new TransferRequest(sourceAcct, destAcct, amountEach, "concurrent test");
                    return mockMvc.perform(post("/api/v1/wallets/transfer")
                                    .contentType("application/json")
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn().getResponse().getStatus();
                }));
            }

            List<Integer> statusCodes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statusCodes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statusCodes).allMatch(status -> status == 200);
        } finally {
            executor.shutdown();
        }

        // 1000 - (10 * 50) = 500.00, regardless of execution interleaving.
        mockMvc.perform(get("/api/v1/wallets/" + sourceAcct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));

        mockMvc.perform(get("/api/v1/wallets/" + destAcct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));
    }
}
