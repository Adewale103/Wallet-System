# Wallet System

The project consist:

1. Creating a user and generating an account (wallet) for them
2. Funding a wallet
3. Transferring funds between accounts

## Tech stack

- Java 21, Spring Boot 3.5
- Spring Data JPA + H2 (in-memory)
- Lombok
- JUnit 5, Mockito, AssertJ, Spring `MockMvc`

## Project structure

```
src/main/java/com/threeline/wallet/
├── WalletSystemApplication.java
├── config/AccountNumberGenerator.java     
├── controller/
│   ├── UserController.java               
│   └── WalletController.java              
├── dto/                                   
├── entity/                               
├── enums/                                
├── exception/                            
├── repository/                           
├── service/
│   ├── UserService.java                  
│   ├── WalletService.java                
│   └── impl/
│       ├── UserServiceImpl.java           
│       ├── WalletServiceImpl.java        
│       └── WalletTransferExecutor.java    
```

## Renaming from the original skeleton

The original skeleton's classes were renamed for clarity, since the assessment evaluates code quality:

| Original | Renamed to | Reason |
|---|---|---|
| `DoTransDto` | `TransferRequest` | name should describe the data, not the action |
| `ServiceCall` | `WalletService` (+ new `UserService`) | split into two interfaces, see below |
| `DoService` | `WalletServiceImpl` (+ new `UserServiceImpl`) | standard `Interface` / `InterfaceImpl` convention |
| `AccountRepo`, `UserRepo`, `WalletBalanceRepo` | `WalletRepository`, `UserRepository` | full word "Repository" is the Spring Data convention; `WalletBalanceRepo` was merged (see below) |
| `Account` + `WalletBalance` (separate entities) | `Wallet` (single entity) | balance never changes independently of its account, so a separate 1:1 table just adds a join for no benefit |

`User` kept its name. `TestApplication` / package `com.example.test` were renamed to `WalletSystemApplication` / `com.threeline.wallet` since "test" isn't a meaningful package name for a real submission.

### Why two services instead of one

The original `ServiceCall`/`DoService` bundled user creation and wallet/transfer logic into a single class. That doesn't hold up under a single-responsibility lens: a class called `WalletService` shouldn't own `User` creation. This submission splits it:

- **`UserService`** owns the `User` entity's lifecycle. `createUserAndAccount()` is the onboarding use case — it creates the `User`, then calls into `WalletService` to open a wallet for that user.
- **`WalletService`** owns the `Wallet` entity's lifecycle (`createWalletFor(User)`, funding, transfers, balance lookup). 
- `UserController` now depends on `UserService`, not `WalletService`.

## Design decisions & assumptions

- **One user → one or more wallets.** The brief only requires one account per user, so `POST /api/v1/users` creates exactly one wallet alongside the user (matching "create a user & generate account" as a single step). 
- **Account numbers** are randomly generated, numeric, 10 digits (NUBAN-style)
- **Identifiers in transfer/fund requests** accept either an account number or an email address 
- **Self-transfer is rejected** (`fromIdentifier == toIdentifier` after resolving to the same account number) with `400 Bad Request`.
- **Insufficient balance** returns `422 Unprocessable Entity`, distinguishing a semantically invalid request (you can't move money you don't have) from a malformed one (`400`).
- **Concurrency:** transfers take a **pessimistic write lock** (`SELECT ... FOR UPDATE`) on both wallet rows before mutating balances (via `WalletTransferExecutor`, a dedicated transactional bean ), and locks are always acquired in a fixed order (ascending account number) regardless of transfer direction, to prevent deadlocks between two transfers running in opposite directions over the same pair of accounts. 
- **Ledger, not just a balance update.** Every funding or transfer operation writes to `wallet_transactions`. A transfer writes two rows (a DEBIT on the source, a CREDIT on the destination) sharing a `reference`.
- **Currency** is assumed single-currency
- 
## Running locally

**Requirements:** Java 21+, Maven 3.8+

```bash
mvn clean install
mvn spring-boot:run
```

The app starts on `http://localhost:9090`. H2 console is available at `http://localhost:9090/h2-console` (JDBC URL: `jdbc:h2:mem:walletdb`, user `sa`, no password).


### Running tests

```bash
mvn test
```

This runs:
- `UserServiceImplTest` — unit tests for onboarding orchestration (mocked `WalletService`): duplicate email rejection, email normalization
- `WalletServiceImplTest` — unit tests for wallet creation, funding, and the transfer retry wrapper (mocked `WalletTransferExecutor`): retries on concurrency conflict, gives up after 5 attempts, does not retry non-concurrency failures
- `WalletTransferExecutorTest` — unit tests for the actual transfer business logic against mocked repositories: insufficient balance, self-transfer, email-based resolution, exact-balance transfer
- `WalletIntegrationTest` — full Spring context + `MockMvc` tests against real H2, including a concurrency test that fires 10 simultaneous transfers on a real fixed thread pool (`ExecutorService`, not the shared `ForkJoinPool`) and asserts every single one returns `200` with no lost updates


## API reference

### Create user + account
```
POST /api/v1/users
Content-Type: application/json

{
  "firstName": "Adewale",
  "lastName": "Ojo",
  "email": "adewale@example.com"
}
```
→ `201 Created`
```json
{
  "userId": 1,
  "firstName": "Adewale",
  "lastName": "Ojo",
  "email": "adewale@example.com",
  "accountNumber": "1234567890",
  "balance": 0.00
}
```

### Fund a wallet
```
POST /api/v1/wallets/fund
Content-Type: application/json

{
  "accountIdentifier": "1234567890",
  "amount": 1000.00,
  "narration": "Initial deposit"
}
```

### Transfer funds
```
POST /api/v1/wallets/transfer
Content-Type: application/json

{
  "fromIdentifier": "1234567890",
  "toIdentifier": "adewale2@example.com",
  "amount": 250.00,
  "narration": "Rent"
}
```
→ `200 OK`
```json
{
  "reference": "TRX-A1B2C3D4E5F6G7H8",
  "status": "SUCCESSFUL",
  "fromAccount": "1234567890",
  "toAccount": "0987654321",
  "amount": 250.00,
  "fromAccountBalanceAfter": 750.00,
  "processedAt": "2026-06-21T10:00:00"
}
```

### Get wallet balance
```
GET /api/v1/wallets/{accountNumberOrEmail}
```

### Error responses
All errors return a consistent shape:
```json
{
  "timestamp": "2026-06-21T10:00:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Insufficient balance in account 1234567890"
}
```

| Status | Meaning |
|---|---|
| 400 | Validation failure / invalid request (e.g. self-transfer, negative amount) |
| 404 | Account or user not found |
| 409 | Duplicate email, or concurrent update conflict |
| 422 | Insufficient balance |

## What I'd add with more time

- Idempotency keys on the transfer endpoint, so a retried request with the same key doesn't double-debit
- Multi-currency support
- Authentication/authorization
- Outbox-pattern event publishing (e.g. `TransferCompletedEvent`) for downstream consumers like notifications
"# Wallet-System" 
