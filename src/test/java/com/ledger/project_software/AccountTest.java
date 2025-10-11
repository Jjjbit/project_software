package com.ledger.project_software;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.domain.*;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class AccountTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    private Ledger testLedger;
    private User testUser;
    private LedgerCategory foodCategory;
    private LedgerCategory salaryCategory;

    @BeforeEach
    public void setUp() {
        testUser = new User("Alice", "password123");
        userRepository.save(testUser);

        testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(foodCategory);
        testLedger.getCategories().add(foodCategory);

        salaryCategory = new LedgerCategory("Salary", CategoryType.INCOME, testLedger);
        ledgerCategoryRepository.save(salaryCategory);
        testLedger.getCategories().add(salaryCategory);
        ledgerRepository.save(testLedger);
        userRepository.save(testUser);
    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testCreateBasicAccount() throws Exception {

        mockMvc.perform(post("/accounts/create-basic-account")
                        .param("accountName", "Test Account")
                        .param("balance", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includedInNetWorth", "true")
                        .param("selectable", "true")
                        .param("type", "CASH")
                        .param("category", "FUNDS"))
                .andExpect(status().isOk())
                .andExpect(content().string("Basic account created successfully"));

        Account createdAccount = accountRepository.findByName("Test Account");
        Assertions.assertNotNull(createdAccount);

        User user = userRepository.findByUsername(testUser.getUsername());
        Assertions.assertEquals(0, user.getTotalAssets().compareTo(new BigDecimal("1000")));
        Assertions.assertEquals(1, user.getAccounts().size());

    }

    @Test
    @WithMockUser(username = "Alice")
    public void  testCreateCreditAccount() throws Exception{
        mockMvc.perform(post("/accounts/create-credit-account")
                        .param("accountName", "Test Account")
                        .param("balance", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includedInNetWorth", "true")
                        .param("selectable", "true")
                        .param("type", "CREDIT_CARD")
                        .param("creditLimit", "100")
                        .param("currentDebt", "")
                        .param("billDate", "")
                        .param("dueDate", ""))
                .andExpect(status().isOk())
                .andExpect(content().string("Credit account created successfully"));

        Account createdAccount = accountRepository.findByName("Test Account");
        Assertions.assertNotNull(createdAccount);

        Assertions.assertEquals(0, ((CreditAccount)createdAccount).getCurrentDebt().compareTo(BigDecimal.ZERO));
        Assertions.assertNull(((CreditAccount)createdAccount).getBillDay());
        Assertions.assertNull(((CreditAccount)createdAccount).getDueDay());

        User updateUser= userRepository.findByUsername(testUser.getUsername());
        Assertions.assertEquals(1, updateUser.getAccounts().size());
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(new BigDecimal("1000")));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateLoanAccount() throws Exception{
        Account account = new BasicAccount("receiving account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(account);

        mockMvc.perform(post("/accounts/create-loan-account")
                        .param("accountName", "Test Account")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includedInNetAsset", "true")
                        .param("receivingAccountId", account.getId().toString())
                        .param("totalPeriods", "36")
                        .param("repaidPeriods", "1")
                        .param("annualInterestRate", "1")
                        .param("loanAmount", "100")
                        .param("repaymentDate", "2025-08-01")
                        .param("repaymentType", "EQUAL_INTEREST"))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan account created successfully"));

        Account createdAccount =accountRepository.findByName("Test Account");
        Assertions.assertNotNull(createdAccount);
        Assertions.assertEquals(1, createdAccount.getTransactions().size());

        User updateUser= userRepository.findByUsername(testUser.getUsername());
        Assertions.assertEquals(2, updateUser.getAccounts().size());

        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(new BigDecimal("1000")));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(new BigDecimal("98.70")));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(new BigDecimal("901.30")));

        //test create LoanAccount without receiving account
        mockMvc.perform(post("/accounts/create-loan-account")
                        .param("accountName", "Test Account 1")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includedInNetAsset", "true")
                        .param("totalPeriods", "36")
                        .param("repaidPeriods", "0")
                        .param("annualInterestRate", "1")
                        .param("loanAmount", "100")
                        .param("repaymentDate", "2025-08-01")
                        .param("repaymentType", "EQUAL_INTEREST"))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan account created successfully"));

        Account createdAccount1 =accountRepository.findByName("Test Account 1");
        Assertions.assertNotNull(createdAccount1);

        User updateUser1= userRepository.findByUsername(testUser.getUsername());
        Assertions.assertEquals(3, updateUser1.getAccounts().size());
        Assertions.assertEquals(0, updateUser1.getTotalAssets().compareTo(new BigDecimal("1000")));
        Assertions.assertEquals(0, updateUser1.getTotalLiabilities().compareTo(new BigDecimal("200.22")));
        Assertions.assertEquals(0, updateUser1.getNetAssets().compareTo(new BigDecimal("799.78")));
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testCreateBorrowingAccount() throws Exception{

        //test create borrowing account without toAccount
        mockMvc.perform(post("/accounts/create-borrowing-account")
                        .param("name", "Bob")
                        .param("amount", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includeInAssets", "true")
                        .param("selectable", "true")
                        .param("date", "2025-11-01"))
                .andExpect(status().isOk())
                .andExpect(content().string("Borrowing account created successfully"));

        Account createdAccount = accountRepository.findByName("Bob");
        Assertions.assertNotNull(createdAccount);
        Assertions.assertEquals(0, createdAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(1, createdAccount.getOutgoingTransactions().size());

        User updateUser= userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updateUser.getAccounts().size());
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(-1000)));

        //create borrowing account with toAccount
        Account account=new BasicAccount(
                "receiving account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);

        mockMvc.perform(post("/accounts/create-borrowing-account")
                        .param("name", "Mike")
                        .param("balance", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includeInAssets", "true")
                        .param("selectable", "true")
                        .param("date", "2025-11-01")
                        .param("toAccountId", String.valueOf(account.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Borrowing account created successfully"));

        Account createdAccount1 = accountRepository.findByName("Mike");
        Assertions.assertNotNull(createdAccount1);
        Assertions.assertEquals(0, createdAccount1.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(1, createdAccount1.getOutgoingTransactions().size());

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertEquals(1, updatedAccount.getIncomingTransactions().size());
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(2000)));

        User updateUser1= userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(3, updateUser1.getAccounts().size());
        Assertions.assertEquals(0, updateUser1.getTotalAssets().compareTo(BigDecimal.valueOf(2000)));
        Assertions.assertEquals(0, updateUser1.getTotalLiabilities().compareTo(BigDecimal.valueOf(2000)));
        Assertions.assertEquals(0, updateUser1.getNetAssets().compareTo(BigDecimal.ZERO));
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testCreateLendingAccount() throws Exception{
        mockMvc.perform(post("/accounts/create-lending-account")
                        .param("name", "Bob")
                        .param("balance", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includeInAssets", "true")
                        .param("selectable", "true")
                        .param("date", "2025-11-01"))
                .andExpect(status().isOk())
                .andExpect(content().string("Lending account created successfully"));


        Account createdAccount = accountRepository.findByName("Bob");
        Assertions.assertNotNull(createdAccount);
        Assertions.assertEquals(0, createdAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(1, createdAccount.getIncomingTransactions().size());


        User updateUser= userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updateUser.getAccounts().size());
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(1000)));


        //create lending account with fromAccount
        Account account=new BasicAccount(
                "receiving account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);

        mockMvc.perform(post("/accounts/create-lending-account")
                        .param("name", "Mike")
                        .param("balance", "1000")
                        .principal(() -> "Alice")
                        .param("note", "Test note")
                        .param("includeInAssets", "true")
                        .param("selectable", "true")
                        .param("date", "2025-11-01")
                        .param("fromAccountId", String.valueOf(account.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Lending account created successfully"));

        Account createdAccount1 = accountRepository.findByName("Mike");
        Assertions.assertNotNull(createdAccount1);
        Assertions.assertEquals(0, createdAccount1.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(1, createdAccount1.getIncomingTransactions().size());

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertEquals(1, updatedAccount.getOutgoingTransactions().size());
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(0)));

        User updateUser1= userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(3, updateUser1.getAccounts().size());
        Assertions.assertEquals(0, updateUser1.getTotalAssets().compareTo(BigDecimal.valueOf(2000)));
        Assertions.assertEquals(0, updateUser1.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updateUser1.getNetAssets().compareTo(BigDecimal.valueOf(2000)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteCreditAccountWithTransactions() throws Exception {
        Account account = new CreditAccount("Test Account",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100), // current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        InstallmentPlan installmentPlan = new InstallmentPlan(
                BigDecimal.valueOf(1200), // total amount
                12, // total periods
                BigDecimal.valueOf(0), // fee rate
                1, // repaid periods
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                account // linked account
        );
        installmentPlanRepository.save(installmentPlan);
        ((CreditAccount) account).addInstallmentPlan(installmentPlan);

        //repay installment plan and part of debt
        ((CreditAccount) account).repayInstallmentPlan(installmentPlan, testLedger);
        ((CreditAccount) account).repayDebt(BigDecimal.valueOf(50), null, testLedger);

        // Add transactions to the account
        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, account, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, account, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        account.addTransaction(transaction1);
        account.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(account);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "true")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account and associated transactions deleted successfully"));

        Account deletedAccount1 = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertNull(deletedAccount1);

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLedger.getTransactions().size());
        Assertions.assertEquals(0, updatedLedger.getTotalIncomeForMonth(YearMonth.now()).compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedLedger.getTotalExpenseForMonth(YearMonth.now()).compareTo(BigDecimal.ZERO));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));

        InstallmentPlan plan = installmentPlanRepository.findById(installmentPlan.getId()).orElse(null);
        Assertions.assertNull(plan);

        Transaction deletedTransaction1 = transactionRepository.findById(transaction1.getId()).orElse(null);
        Assertions.assertNull(deletedTransaction1);

        Transaction deletedTransaction2 = transactionRepository.findById(transaction2.getId()).orElse(null);
        Assertions.assertNull(deletedTransaction2);

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedSalaryCategory.getTransactions().size());

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteCreditAccountWithoutTransactions() throws Exception{
        Account account = new CreditAccount("Test Account",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100), // current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        InstallmentPlan installmentPlan = new InstallmentPlan(
                BigDecimal.valueOf(1200), // total amount
                12, // total periods
                BigDecimal.valueOf(0), // fee rate
                1, // repaid periods
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                account // linked account
        );
        installmentPlanRepository.save(installmentPlan);
        ((CreditAccount) account).addInstallmentPlan(installmentPlan);

        ((CreditAccount) account).repayInstallmentPlan(installmentPlan, testLedger);
        ((CreditAccount) account).repayDebt(BigDecimal.valueOf(50), null, testLedger);

        // Add transactions to the account
        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, account, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, account, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        account.addTransaction(transaction1);
        account.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(account);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account disassociated from transactions and deleted successfully"));

        Account deletedAccount = accountRepository.findById(account.getId()).orElse(null); //cerca account da database
        Assertions.assertNull(deletedAccount);

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(4, updatedLedger.getTransactions().size());
        Assertions.assertEquals(0, updatedLedger.getTotalIncomeForMonth(YearMonth.now()).compareTo(BigDecimal.valueOf(1500)));
        Assertions.assertEquals(0, updatedLedger.getTotalExpenseForMonth(YearMonth.now()).compareTo(BigDecimal.valueOf(10)));

        InstallmentPlan plan = installmentPlanRepository.findById(installmentPlan.getId()).orElse(null);
        Assertions.assertNull(plan);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));

        Transaction updatedTransaction1 = transactionRepository.findById(transaction1.getId()).orElse(null);
        Assertions.assertNotNull(updatedTransaction1);

        Transaction updatedTransaction2 = transactionRepository.findById(transaction2.getId()).orElse(null);
        Assertions.assertNotNull(updatedTransaction2);

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedSalaryCategory.getTransactions().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLoanAccountWithTransactions() throws Exception{
        Account receivingAccount = new BasicAccount("receiving account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(receivingAccount);
        testUser.getAccounts().add(receivingAccount);

        Account account = new LoanAccount("Test Account",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.valueOf(1), // annual interest rate
                BigDecimal.valueOf(100), // loan amount
                receivingAccount,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        ((LoanAccount) account).repayLoan(null, testLedger);
        ((LoanAccount) account).repayLoan(receivingAccount, testLedger);

        accountRepository.save(account);
        accountRepository.save(receivingAccount);
        ledgerRepository.save(testLedger);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "true")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account and associated transactions deleted successfully"));

        Account deletedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertNull(deletedAccount);

        Account updatedReceivingAccount = accountRepository.findById(receivingAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updatedReceivingAccount.getTransactions().size());


        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLedger.getTransactions().size());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.valueOf(997.18)));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.valueOf(997.18)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLoanAccountWithoutTransactions() throws Exception{
        Account receivingAccount = new BasicAccount("receiving account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(receivingAccount);
        testUser.getAccounts().add(receivingAccount);

        Account account = new LoanAccount("Test Account",
                testUser,
                null,
                true,
                36,
                1,
                BigDecimal.valueOf(1), // annual interest rate
                BigDecimal.valueOf(100), // loan amount
                receivingAccount,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);
        //remaing loan amount should be 98.70

        ((LoanAccount) account).repayLoan(null, testLedger);
        ((LoanAccount) account).repayLoan(receivingAccount, testLedger);

        accountRepository.save(account);
        accountRepository.save(receivingAccount);
        ledgerRepository.save(testLedger);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account disassociated from transactions and deleted successfully"));

        Account deletedAccount = accountRepository.findById(account.getId()).orElse(null); //cerca account da database
        Assertions.assertNull(deletedAccount);

        Account updatedReceivingAccount = accountRepository.findById(receivingAccount.getId()).orElse(null);
        Assertions.assertEquals(1, updatedReceivingAccount.getTransactions().size());

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getTransactions().size());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(new BigDecimal("997.18")));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(new BigDecimal("0")));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(new BigDecimal("997.18")));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteBasicAccountWithTransactions() throws Exception {
        Account account = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        // Add transactions to the account
        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, account, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, account, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        account.addTransaction(transaction1);
        account.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(account);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "true")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account and associated transactions deleted successfully"));

        Account deletedAccount = accountRepository.findById(account.getId()).orElse(null); //cerca account da database
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLedger.getTransactions().size());

        Transaction deletedTransaction1 = transactionRepository.findById(transaction1.getId()).orElse(null);
        Assertions.assertNull(deletedTransaction1);

        Transaction deletedTransaction2 = transactionRepository.findById(transaction2.getId()).orElse(null);
        Assertions.assertNull(deletedTransaction2);

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getTransactions().size());

        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedSalaryCategory.getTransactions().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteBasicAccountWithoutTransactions() throws Exception {
        Account account = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        // Add transactions to the account
        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, account, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, account, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);


        account.addTransaction(transaction1);
        account.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(account);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + account.getId())
                        .param("deleteTransactions", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account disassociated from transactions and deleted successfully"));

        Account deletedAccount = accountRepository.findById(account.getId()).orElse(null); //cerca account da database
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getTransactions().size());

        Assertions.assertEquals(0, updatedLedger.getTotalIncomeForMonth(YearMonth.now()).compareTo(BigDecimal.valueOf(1500)));
        Assertions.assertEquals(0, updatedLedger.getTotalExpenseForMonth(YearMonth.now()).compareTo(BigDecimal.valueOf(10)));

        Transaction updatedTransaction1 = transactionRepository.findById(transaction1.getId()).orElse(null);
        Assertions.assertNotNull(updatedTransaction1);

        Transaction updatedTransaction2 = transactionRepository.findById(transaction2.getId()).orElse(null);
        Assertions.assertNotNull(updatedTransaction2);

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedFoodCategory.getTransactions().size());

        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedSalaryCategory.getTransactions().size());

    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testDeleteBorrowingAccountWithTransactions() throws Exception{
        BorrowingAccount borrowingAccount = new BorrowingAccount("Test Borrowing Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(borrowingAccount);
        testUser.getAccounts().add(borrowingAccount);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, borrowingAccount, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, borrowingAccount, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        borrowingAccount.addTransaction(transaction1);
        borrowingAccount.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(borrowingAccount);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + borrowingAccount.getId())
                        .param("deleteTransactions", "true")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account and associated transactions deleted successfully"));

        Account deletedAccount = accountRepository.findById(borrowingAccount.getId()).orElse(null);
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getAccounts().size());

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLedger.getTransactions().size());

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedSalaryCategory.getTransactions().size());
    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testDeleteBorrowingAccountWithoutTransactions() throws Exception{
        BorrowingAccount borrowingAccount = new BorrowingAccount("Test Borrowing Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(borrowingAccount);
        testUser.getAccounts().add(borrowingAccount);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, borrowingAccount, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, borrowingAccount, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        borrowingAccount.addTransaction(transaction1);
        borrowingAccount.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(borrowingAccount);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + borrowingAccount.getId())
                        .param("deleteTransactions", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account disassociated from transactions and deleted successfully"));

        Account deletedAccount = accountRepository.findById(borrowingAccount.getId()).orElse(null);
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getAccounts().size());

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getTransactions().size());

        Assertions.assertEquals(2, transactionRepository.findAll().size());

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedSalaryCategory.getTransactions().size());
    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testDeleteLendingAccountWithTransactions() throws Exception{
        LendingAccount lendingAccount = new LendingAccount("Test Lending Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(lendingAccount);
        testUser.getAccounts().add(lendingAccount);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, lendingAccount, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, lendingAccount, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        lendingAccount.addTransaction(transaction1);
        lendingAccount.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(lendingAccount);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + lendingAccount.getId())
                        .param("deleteTransactions", "true")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account and associated transactions deleted successfully"));

        Account deletedAccount = accountRepository.findById(lendingAccount.getId()).orElse(null);
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getAccounts().size());

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLedger.getTransactions().size());

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedSalaryCategory.getTransactions().size());

        Assertions.assertEquals(0, transactionRepository.findAll().size());
    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testDeleteLendingAccountWithoutTransactions() throws Exception{
        LendingAccount lendingAccount = new LendingAccount("Test Lending Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(lendingAccount);
        testUser.getAccounts().add(lendingAccount);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, lendingAccount, testLedger, foodCategory);
        Transaction transaction2 = new Income(LocalDate.now(), BigDecimal.valueOf(1500), null, lendingAccount, testLedger, salaryCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);

        lendingAccount.addTransaction(transaction1);
        lendingAccount.addTransaction(transaction2);
        testLedger.getTransactions().add(transaction1);
        testLedger.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        salaryCategory.getTransactions().add(transaction2);

        accountRepository.save(lendingAccount);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(salaryCategory);

        mockMvc.perform(delete("/accounts/" + lendingAccount.getId())
                        .param("deleteTransactions", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account disassociated from transactions and deleted successfully"));

        Account deletedAccount = accountRepository.findById(lendingAccount.getId()).orElse(null);
        Assertions.assertNull(deletedAccount);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getAccounts().size());

        Ledger updatedLedger = ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getTransactions().size());

        Assertions.assertEquals(2, transactionRepository.findAll().size());

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedFoodCategory.getTransactions().size());
        LedgerCategory updatedSalaryCategory = ledgerCategoryRepository.findById(salaryCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedSalaryCategory.getTransactions().size());

    }

    @Test
    @WithMockUser(username = "Alice") // Simulating an authenticated user
    public void testHideAccount() throws Exception {

        //hide basic account
        Account account = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/hide")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account hidden successfully"));


        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertTrue(updatedAccount.getHidden());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(1, updatedUser.getAccounts().size());

        //hide credit account
        account = new CreditAccount("Test Account",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100), // current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/hide")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account hidden successfully"));

        Account updatedCreditAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertTrue(updatedCreditAccount.getHidden());

        User updatedCreditUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedCreditUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedCreditUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedCreditUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(2, updatedCreditUser.getAccounts().size());

        //hide loan account
        account = new LoanAccount("Test Account",
                testUser,
                null,
                true,
                36,
                1,
                BigDecimal.valueOf(1), // annual interest rate
                BigDecimal.valueOf(100), // loan amount
                null,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/hide")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account hidden successfully"));
        Account updatedLoanAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertTrue(updatedLoanAccount.getHidden());

        User updatedLoanUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLoanUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedLoanUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedLoanUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(3, updatedLoanUser.getAccounts().size());

        //hide borrowing account
        account = new BorrowingAccount("Test Borrowing Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/hide")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account hidden successfully"));
        Account updatedBorrowingAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertTrue(updatedBorrowingAccount.getHidden());

        User updatedBorrowingUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedBorrowingUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedBorrowingUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedBorrowingUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(4, updatedBorrowingUser.getAccounts().size());

        //hide lending account
        account = new LendingAccount("Test Lending Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/hide")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account hidden successfully"));

        Account updatedLendingAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertTrue(updatedLendingAccount.getHidden());

        User updatedLendingUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedLendingUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedLendingUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedLendingUser.getNetAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(5, updatedLendingUser.getAccounts().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditBasicAccount() throws Exception {
        Account account = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/edit-basic-account")
                        .param("name", "Updated Account")
                        .param("balance", "1500")
                        .param("notes", "Updated note")
                        .param("includedInNetAsset", "false")
                        .param("selectable", "false")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account edited successfully"));

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertNotNull(updatedAccount);

        Assertions.assertEquals("Updated Account", updatedAccount.getName());

        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1500)));

        Assertions.assertEquals("Updated note", updatedAccount.getNotes());

        Assertions.assertFalse(updatedAccount.getIncludedInNetAsset());

        Assertions.assertFalse(updatedAccount.getSelectable());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditCreditAccount() throws Exception{
        Account account = new CreditAccount("Test Account",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(0),
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/edit-credit-account")
                        .param("name", "Updated Account")
                        .param("balance", "1500")
                        .param("notes", "Updated note")
                        .param("includedInNetAsset", "true")
                        .param("selectable", "false")
                        .param("creditLimit", "900")
                        .param("currentDebt", "10")
                        .param("billDate", "1")
                        .param("dueDate", "")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Credit account edited successfully"));

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertNotNull(updatedAccount);

        Assertions.assertEquals(0, ((CreditAccount) updatedAccount).getCreditLimit().compareTo(BigDecimal.valueOf(900)));

        Assertions.assertEquals(0, ((CreditAccount) updatedAccount).getCurrentDebt().compareTo(BigDecimal.valueOf(10)));

        Assertions.assertEquals(1, ((CreditAccount) updatedAccount).getBillDay());

        Assertions.assertNull(((CreditAccount) updatedAccount).getDueDay());

        Assertions.assertEquals("Updated Account", updatedAccount.getName());

        Assertions.assertEquals(BigDecimal.valueOf(1500), updatedAccount.getBalance());

        Assertions.assertEquals("Updated note", updatedAccount.getNotes());

        Assertions.assertTrue(updatedAccount.getIncludedInNetAsset());

        Assertions.assertFalse(updatedAccount.getSelectable());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.valueOf(1500)));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(10)));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.valueOf(1490)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditLoanAccount() throws Exception{
        Account account = new LoanAccount("Test Account",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.valueOf(0),
                BigDecimal.valueOf(100),
                null,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/edit-loan-account")
                        .param("name", "Updated Account")
                        .param("notes", "Updated note")
                        .param("includedInNetAsset", "true")
                        .param("selectable", "false")
                        .param("totalPeriods", "" )
                        .param("repaidPeriods", "1")
                        .param("annualInterestRate", "1")
                        .param("loanAmount", "")
                        .param("repaymentDate", "")
                        .param("repaymentType", "")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan account edited successfully"));

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertEquals(0, ((LoanAccount) updatedAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(98.70)));

        Assertions.assertEquals(1, ((LoanAccount) updatedAccount).getRepaidPeriods());

        Assertions.assertEquals(36, ((LoanAccount) updatedAccount).getTotalPeriods());

        Assertions.assertEquals("Updated Account", updatedAccount.getName());

        Assertions.assertEquals("Updated note", updatedAccount.getNotes());

        Assertions.assertTrue(updatedAccount.getIncludedInNetAsset());

        Assertions.assertFalse(updatedAccount.getSelectable());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(98.70)));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.valueOf(-98.70)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditBorrowingAccount() throws Exception{
        Account account = new BorrowingAccount("Test Account",
                BigDecimal.valueOf(100), //balance is debt amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/edit-borrowing-account")
                        .param("name", "Updated Account")
                        .param("notes", "Updated note")
                        .param("selectable", "false")
                        .param("balance", "1000")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Borrowing account updated successfully"));

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals("Updated Account", updatedAccount.getName());
        Assertions.assertEquals("Updated note", updatedAccount.getNotes());
        Assertions.assertTrue(updatedAccount.getIncludedInNetAsset());
        Assertions.assertFalse(updatedAccount.getSelectable());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().intValue());
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.valueOf(-1000)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditLendingAccount() throws Exception{
        Account account = new LendingAccount("Test Account",
                BigDecimal.valueOf(100), //balance is lent amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(account);
        testUser.getAccounts().add(account);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + account.getId() + "/edit-lending-account")
                        .param("name", "Bob")
                        .param("note", "Updated note")
                        .param("selectable", "false")
                        .param("balance", "1000")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("LendingAccount updated successfully"));

        Account updatedAccount = accountRepository.findById(account.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals("Bob", updatedAccount.getName());
        Assertions.assertEquals("Updated note", updatedAccount.getNotes());
        Assertions.assertTrue(updatedAccount.getIncludedInNetAsset());
        Assertions.assertFalse(updatedAccount.getSelectable());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalAssets().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().intValue());
        Assertions.assertEquals(0, updatedUser.getNetAssets().compareTo(BigDecimal.valueOf(1000)));
    }



    @Test
    @WithMockUser(username = "Alice")
    public void testCreditAccount() throws Exception{
        //test credit BasicAccount
        Account basicAccount = new BasicAccount("account1",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(basicAccount);
        testUser.getAccounts().add(basicAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + basicAccount.getId() + "/credit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("credit account"));

        Account updateAccount=accountRepository.findByName("account1");
        Assertions.assertNotNull(updateAccount);
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(BigDecimal.valueOf(1010)));

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(1010)));


        //test credit CreditAccount
        Account creditAccount = new CreditAccount("account2",
                BigDecimal.valueOf(1000), //balance
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000), //credit limit
                BigDecimal.valueOf(0), //current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(creditAccount);
        testUser.getAccounts().add(creditAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + creditAccount.getId() + "/credit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("credit account"));

        Account updateAccount2=accountRepository.findByName("account2");
        Assertions.assertNotNull(updateAccount2);
        Assertions.assertEquals(0, updateAccount2.getBalance().compareTo(BigDecimal.valueOf(1010)));

        User updateUser1=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser1.getTotalAssets().compareTo(BigDecimal.valueOf(2020)));

        //test credit LoanAccount
        Account loanAccount = new LoanAccount("account3",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.valueOf(0), // annual interest rate
                BigDecimal.valueOf(100), //loan amount is debt
                null,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(loanAccount);
        testUser.getAccounts().add(loanAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + loanAccount.getId() + "/credit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot credit a loan account"));

        User updateUser2=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser2.getTotalAssets().compareTo(BigDecimal.valueOf(2020)));
        Assertions.assertEquals(0, updateUser2.getTotalLiabilities().compareTo(BigDecimal.valueOf(100)));
        Assertions.assertEquals(0, updateUser2.getNetAssets().compareTo(BigDecimal.valueOf(1920)));

        //test credit BorrowingAccount
        Account borrowingAccount = new BorrowingAccount("account4",
                BigDecimal.valueOf(100), //balance is debt
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(borrowingAccount);
        testUser.getAccounts().add(borrowingAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + borrowingAccount.getId() + "/credit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("credit account"));

        Account updateAccount4=accountRepository.findByName("account4");
        Assertions.assertEquals(0, updateAccount4.getBalance().compareTo(BigDecimal.valueOf(90)));

        User updateUser3=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser3.getTotalAssets().compareTo(BigDecimal.valueOf(2020)));
        Assertions.assertEquals(0, updateUser3.getTotalLiabilities().compareTo(BigDecimal.valueOf(190)));
        Assertions.assertEquals(0, updateUser3.getNetAssets().compareTo(BigDecimal.valueOf(1830)));

        //test credit LendingAccount
        Account lendingAccount = new LendingAccount("account5",
                BigDecimal.valueOf(100), //balance is lent amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(lendingAccount);
        testUser.getAccounts().add(lendingAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + lendingAccount.getId() + "/credit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("credit account"));

        Account updateAccount5=accountRepository.findByName("account5");
        Assertions.assertEquals(0, updateAccount5.getBalance().compareTo(BigDecimal.valueOf(110)));

        User updateUser4=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser4.getTotalAssets().compareTo(BigDecimal.valueOf(2130)));
        Assertions.assertEquals(0, updateUser4.getTotalLiabilities().compareTo(BigDecimal.valueOf(190)));
        Assertions.assertEquals(0, updateUser4.getNetAssets().compareTo(BigDecimal.valueOf(1940)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDebitAccount() throws Exception{
        //test credit BasicAccount
        Account basicAccount = new BasicAccount("account1",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(basicAccount);
        testUser.getAccounts().add(basicAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + basicAccount.getId() + "/debit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("debit account"));

        Account updateAccount=accountRepository.findByName("account1");
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(BigDecimal.valueOf(990)));

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(990)));

        //test credit CreditAccount with balance=0
        Account creditAccount = new CreditAccount("account2",
                BigDecimal.valueOf(0), //balance=0
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000), //credit limit
                BigDecimal.valueOf(0), //current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(creditAccount);
        testUser.getAccounts().add(creditAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + creditAccount.getId() + "/debit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("debit account"));

        Account updateAccount2=accountRepository.findByName("account2");
        Assertions.assertEquals(0, updateAccount2.getBalance().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, ((CreditAccount)updateAccount2).getCurrentDebt().compareTo(BigDecimal.valueOf(10)));

        User updateUser1=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser1.getTotalAssets().compareTo(BigDecimal.valueOf(990)));
        Assertions.assertEquals(0, updateUser1.getTotalLiabilities().compareTo(BigDecimal.valueOf(10)));
        Assertions.assertEquals(0, updateUser1.getNetAssets().compareTo(BigDecimal.valueOf(980)));

        //test debit LoanAccount
        Account loanAccount = new LoanAccount("account3",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.valueOf(0), //annual interest rate=0
                BigDecimal.valueOf(100), //loan amount is debt
                null,
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(loanAccount);
        testUser.getAccounts().add(loanAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + loanAccount.getId() + "/debit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot debit a loan account"));

        //test debit BorrowingAccount
        Account borrowingAccount = new BorrowingAccount("account4",
                BigDecimal.valueOf(100), //balance is debt
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(borrowingAccount);
        testUser.getAccounts().add(borrowingAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + borrowingAccount.getId() + "/debit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("debit account"));

        Account updateAccount4=accountRepository.findByName("account4");
        Assertions.assertEquals(0, updateAccount4.getBalance().compareTo(BigDecimal.valueOf(110)));

        User updateUser2=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser2.getTotalAssets().compareTo(BigDecimal.valueOf(990)));
        Assertions.assertEquals(0, updateUser2.getTotalLiabilities().compareTo(BigDecimal.valueOf(220)));
        Assertions.assertEquals(0, updateUser2.getNetAssets().compareTo(BigDecimal.valueOf(770)));

        //test debit LendingAccount
        Account lendingAccount = new LendingAccount("account5",
                BigDecimal.valueOf(100), //balance is lent amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(lendingAccount);
        testUser.getAccounts().add(lendingAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + lendingAccount.getId() + "/debit")
                        .param("amount", "10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("debit account"));

        Account updateAccount5=accountRepository.findByName("account5");
        Assertions.assertEquals(0, updateAccount5.getBalance().compareTo(BigDecimal.valueOf(90)));

        User updateUser3=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser3.getTotalAssets().compareTo(BigDecimal.valueOf(1080)));
        Assertions.assertEquals(0, updateUser3.getTotalLiabilities().compareTo(BigDecimal.valueOf(220)));
        Assertions.assertEquals(0, updateUser3.getNetAssets().compareTo(BigDecimal.valueOf(860)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testRepayDebt() throws Exception{
        //test repayDebt CreditAccount
        Account creditAccount = new CreditAccount("account2",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(100), //credit limit
                BigDecimal.valueOf(100), //current debt
                null,
                null,
                AccountType.CREDIT_CARD
        );
        accountRepository.save(creditAccount);
        testUser.getAccounts().add(creditAccount);

        Account fromAccount = new BasicAccount("from account",
                BigDecimal.valueOf(200),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(fromAccount);
        testUser.getAccounts().add(fromAccount);
        userRepository.save(testUser);

        //test repayDebt without ledger
        mockMvc.perform(put("/accounts/" + creditAccount.getId() + "/repay-debt")
                        .param("amount", "50")
                        .param("fromAccountId", fromAccount.getId().toString())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Debt repaid successfully"));

        Account updateCreditAccount=accountRepository.findByName("account2");
        Assertions.assertNotNull(updateCreditAccount);
        Assertions.assertEquals(0, ((CreditAccount)updateCreditAccount).getCurrentDebt().compareTo(BigDecimal.valueOf(50)));
        Assertions.assertEquals(1, updateCreditAccount.getTransactions().size());

        Account updateFromAccount=accountRepository.findByName("from account");
        Assertions.assertEquals(0, updateFromAccount.getBalance().compareTo(BigDecimal.valueOf(150)));
        Assertions.assertEquals(1, updateFromAccount.getTransactions().size());

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(1150)));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(50)));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(1100)));
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testRepayLoan() throws Exception{
        //test repayLoan LoanAccount
        Account loanAccount = new LoanAccount("account2",
                testUser,
                null,
                true,
                36,
                1,
                BigDecimal.valueOf(1), //annual interest rate
                BigDecimal.valueOf(100), //loan amount
                null, //receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        accountRepository.save(loanAccount);
        testUser.getAccounts().add(loanAccount);

        Account fromAccount = new BasicAccount("from account",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(fromAccount);
        testUser.getAccounts().add(fromAccount);
        userRepository.save(testUser);

        //repayloan with no specific amount
        mockMvc.perform(put("/accounts/" + loanAccount.getId() + "/repay-loan")
                        .principal(() -> "Alice")
                        .param("fromAccountId",  String.valueOf(fromAccount.getId()))
                        .param("ledgerId", String.valueOf(testLedger.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan repaid successfully"));

        Account updateLoanAccount=accountRepository.findByName("account2");
        Assertions.assertEquals(2, ((LoanAccount)updateLoanAccount).getRepaidPeriods());
        Assertions.assertEquals(0, ((LoanAccount)updateLoanAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(95.88)));
        Assertions.assertEquals(1, updateLoanAccount.getTransactions().size());

        Account updateFromAccount=accountRepository.findByName("from account");
        Assertions.assertEquals(0, updateFromAccount.getBalance().compareTo(BigDecimal.valueOf(1997.18)));
        Assertions.assertEquals(1, updateFromAccount.getTransactions().size());

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(1997.18)));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(95.88)));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(1901.30)));

        Ledger updateLedger=ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(1, updateLedger.getTransactions().size());

        Assertions.assertEquals(1, transactionRepository.findAll().size());

        //repayLoan with specific amount
        mockMvc.perform(put("/accounts/" + loanAccount.getId() + "/repay-loan")
                        .principal(() -> "Alice")
                        .param("fromAccountId", String.valueOf(fromAccount.getId()))
                        .param("amount", "28.50")
                        .param("ledgerId", String.valueOf(testLedger.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan repaid successfully"));

        Account updateLoanAccount2=accountRepository.findByName("account2");
        Assertions.assertEquals(12, ((LoanAccount)updateLoanAccount2).getRepaidPeriods());
        Assertions.assertEquals(0, ((LoanAccount)updateLoanAccount2).getRemainingAmount().compareTo(BigDecimal.valueOf(67.38)));
        Assertions.assertEquals(2, updateLoanAccount2.getTransactions().size());


        Account updateFromAccount2=accountRepository.findByName("from account");
        Assertions.assertEquals(0, updateFromAccount2.getBalance().compareTo(BigDecimal.valueOf(1968.68)));
        Assertions.assertEquals(2, updateFromAccount2.getTransactions().size());

        User updateUser2=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser2.getTotalAssets().compareTo(BigDecimal.valueOf(1968.68)));
        Assertions.assertEquals(0, updateUser2.getTotalLiabilities().compareTo(BigDecimal.valueOf(67.38)));
        Assertions.assertEquals(0, updateUser2.getNetAssets().compareTo(BigDecimal.valueOf(1901.30)));

        //repayLoan with specific amount and no fromAccountId
        mockMvc.perform(put("/accounts/" + loanAccount.getId() + "/repay-loan")
                        .principal(() -> "Alice")
                        .param("fromAccountId", "")
                        .param("amount", "60")
                        .param("ledgerId", String.valueOf(testLedger.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Loan repaid successfully"));

        Account updateLoanAccount3=accountRepository.findByName("account2");
        Assertions.assertEquals(33, ((LoanAccount)updateLoanAccount3).getRepaidPeriods());
        Assertions.assertEquals(0, ((LoanAccount)updateLoanAccount3).getRemainingAmount().compareTo(BigDecimal.valueOf(7.38)));
        Assertions.assertEquals(3, updateLoanAccount3.getTransactions().size());

        Account updateFromAccount3=accountRepository.findByName("from account");
        Assertions.assertEquals(0, updateFromAccount3.getBalance().compareTo(BigDecimal.valueOf(1968.68)));
        Assertions.assertEquals(2, updateFromAccount3.getTransactions().size());

        User updateUser3=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser3.getTotalAssets().compareTo(BigDecimal.valueOf(1968.68)));
        Assertions.assertEquals(0, updateUser3.getTotalLiabilities().compareTo(BigDecimal.valueOf(7.38)));
        Assertions.assertEquals(0, updateUser3.getNetAssets().compareTo(BigDecimal.valueOf(1961.30)));


    }

    @Test
    @WithMockUser(username = "Alice")
    public void testRepayBorrowing() throws Exception{
        //test repayBorrowing BorrowingAccount
        Account borrowingAccount = new BorrowingAccount("account2",
                BigDecimal.valueOf(100), //balance is debt amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(borrowingAccount);
        testUser.getAccounts().add(borrowingAccount);

        Account fromAccount = new BasicAccount("from account",
                BigDecimal.valueOf(200),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(fromAccount);
        testUser.getAccounts().add(fromAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + borrowingAccount.getId() + "/repay-borrowing")
                        .param("amount", "50")
                        .param("fromAccountId", String.valueOf(fromAccount.getId()))
                        .principal(() -> "Alice")
                        .param("ledgerId", String.valueOf(testLedger.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Repayment successful"));

        Account updateBorrowingAccount=accountRepository.findById(borrowingAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateBorrowingAccount.getBalance().compareTo(BigDecimal.valueOf(50)));
        Assertions.assertEquals(1, updateBorrowingAccount.getTransactions().size());
        Assertions.assertEquals(1, updateBorrowingAccount.getIncomingTransactions().size());

        Account updateFromAccount=accountRepository.findById(fromAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateFromAccount.getBalance().compareTo(BigDecimal.valueOf(150)));
        Assertions.assertEquals(1, updateFromAccount.getTransactions().size());
        Assertions.assertEquals(1, updateFromAccount.getOutgoingTransactions().size());

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(150)));
        Assertions.assertEquals(0, updateUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(50)));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(100)));
        Assertions.assertEquals(2, updateUser.getAccounts().size());

        Ledger updateLedger=ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(1, updateLedger.getTransactions().size());

        Assertions.assertEquals(1, transactionRepository.findAll().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testReceiveLending() throws Exception{
        //test receiveLending LendingAccount
        Account lendingAccount = new LendingAccount("account2",
                BigDecimal.valueOf(100), //balance is lent amount
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        accountRepository.save(lendingAccount);
        testUser.getAccounts().add(lendingAccount);

        Account toAccount = new BasicAccount("to account",
                BigDecimal.valueOf(200),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(toAccount);
        testUser.getAccounts().add(toAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/accounts/" + lendingAccount.getId() + "/receive-lending")
                        .param("amount", "50")
                        .param("toAccountId", String.valueOf(toAccount.getId()))
                        .principal(() -> "Alice")
                        .param("ledgerId", String.valueOf(testLedger.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Lending received successfully"));

        Account updateLendingAccount=accountRepository.findById(lendingAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateLendingAccount.getBalance().compareTo(BigDecimal.valueOf(50)));
        Assertions.assertEquals(1, updateLendingAccount.getTransactions().size());
        Assertions.assertEquals(1, updateLendingAccount.getOutgoingTransactions().size());

        Account updateToAccount=accountRepository.findById(toAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateToAccount.getBalance().compareTo(BigDecimal.valueOf(250)));
        Assertions.assertEquals(1, updateToAccount.getTransactions().size());
        Assertions.assertEquals(1, updateToAccount.getIncomingTransactions().size());

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(300)));
        Assertions.assertEquals(0, updateUser.getNetAssets().compareTo(BigDecimal.valueOf(300)));
        Assertions.assertEquals(2, updateUser.getAccounts().size());

        Ledger updateLedger=ledgerRepository.findById(testLedger.getId()).orElse(null);
        Assertions.assertEquals(1, updateLedger.getTransactions().size());

        Assertions.assertEquals(1, transactionRepository.findAll().size());
    }


}
