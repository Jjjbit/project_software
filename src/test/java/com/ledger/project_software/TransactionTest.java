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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class TransactionTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private AccountRepository accountRepository;

    private User testUser;
    private Ledger testLedger1;
    private Account testAccount;
    private LedgerCategory testCategory1;
    private LedgerCategory testCategory2;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @BeforeEach
    public void setUp(){
        testUser=new User("Alice", "pass123");
        userRepository.save(testUser);

        testLedger1 = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger1);

        testAccount = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount);
        testUser.getAccounts().add(testAccount);

        testCategory1=new LedgerCategory("Category 1", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(testCategory1);
        testCategory2=new LedgerCategory("Category 2", CategoryType.INCOME, testLedger1);
        ledgerCategoryRepository.save(testCategory2);

        testLedger1.getCategories().add(testCategory1);
        testLedger1.getCategories().add(testCategory2);
        ledgerRepository.save(testLedger1);

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateExpense() throws Exception {
        mockMvc.perform(post("/transactions/create")
                        .principal(() -> "Alice")
                        .param("date", "2025-10-01")
                        .param("amount", "150.00")
                        .param("fromAccountId", String.valueOf(testAccount.getId()))
                        .param("ledgerId", String.valueOf(testLedger1.getId()))
                        .param("categoryId", String.valueOf(testCategory1.getId()))
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction created successfully"));

        Transaction createdTransaction=transactionRepository.findAll().get(0);
        Assertions.assertNotNull(createdTransaction);

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 150 expense = 850
        assert(updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850)) == 0);
        Assertions.assertTrue(updatedAccount.getOutgoingTransactions().contains(createdTransaction));
        Assertions.assertEquals(TransactionType.EXPENSE, createdTransaction.getType());

        Ledger updatedLedger=ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertTrue(updatedLedger.getTransactions().contains(createdTransaction));

        LedgerCategory updatedCategory=ledgerCategoryRepository.findById(testCategory1.getId()).orElse(null);
        Assertions.assertTrue(updatedCategory.getTransactions().contains(createdTransaction));

        User updatedUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(850)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateIncome() throws Exception {
        mockMvc.perform(post("/transactions/create")
                        .principal(() -> "Alice")
                        .param("date", "2025-10-01")
                        .param("amount", "200.00")
                        .param("toAccountId", String.valueOf(testAccount.getId()))
                        .param("ledgerId", String.valueOf(testLedger1.getId()))
                        .param("categoryId", String.valueOf(testCategory2.getId()))
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction created successfully"));

        Transaction createdTransaction = transactionRepository.findAll().get(0);
        Assertions.assertNotNull(createdTransaction);

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 + 200 income = 1200
        assert (updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1200)) == 0);
        Assertions.assertTrue(updatedAccount.getIncomingTransactions().contains(createdTransaction));
        Assertions.assertEquals(TransactionType.INCOME, createdTransaction.getType());

        Ledger updatedLedger = ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertTrue(updatedLedger.getTransactions().contains(createdTransaction));

        LedgerCategory updatedCategory=ledgerCategoryRepository.findById(testCategory2.getId()).orElse(null);
        Assertions.assertTrue(updatedCategory.getTransactions().contains(createdTransaction));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1200)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateTransfer() throws Exception {
        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        mockMvc.perform(post("/transactions/create")
                        .principal(() -> "Alice")
                        .param("date", "2025-10-01")
                        .param("amount", "300.00")
                        .param("fromAccountId", String.valueOf(testAccount.getId()))
                        .param("toAccountId", String.valueOf(testAccount2.getId()))
                        .param("ledgerId", String.valueOf(testLedger1.getId()))
                        .param("type", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction created successfully"));

        Transaction createdTransaction = transactionRepository.findAll().get(0);
        Assertions.assertNotNull(createdTransaction);

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 300 transfer = 700
        assert (updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(700)) == 0);
        Assertions.assertTrue(updatedAccount1.getOutgoingTransactions().contains(createdTransaction));
        Assertions.assertEquals(TransactionType.TRANSFER, createdTransaction.getType());

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 300 transfer = 800
        assert (updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(800)) == 0);
        Assertions.assertTrue(updatedAccount2.getIncomingTransactions().contains(createdTransaction));

        Ledger updatedLedger = ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertTrue(updatedLedger.getTransactions().contains(createdTransaction));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1500)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteExpense() throws Exception {
        Transaction tx1=new Expense(
                LocalDate.now(),
                BigDecimal.valueOf(100),
                null,
                testAccount,
                testLedger1,
                testCategory1
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        //testCategory1.addTransaction(tx1);
        testCategory1.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory1);

        mockMvc.perform(delete("/transactions/"+tx1.getId()+ "/delete")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction deleted successfully"));

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 100 expense + 100 rollback = 1000
        assert (updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)) == 0);
        Assertions.assertFalse(updatedAccount.getOutgoingTransactions().contains(tx1));

        Ledger updatedLedger = ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertFalse(updatedLedger.getTransactions().contains(tx1));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1000)));

        LedgerCategory updatedCategory=ledgerCategoryRepository.findById(testCategory1.getId()).orElse(null);
        Assertions.assertFalse(updatedCategory.getTransactions().contains(tx1));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteIncome() throws Exception {
        Transaction tx1=new Income(
                LocalDate.now(),
                BigDecimal.valueOf(200),
                null,
                testAccount,
                testLedger1,
                testCategory2
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        //testCategory2.addTransaction(tx1);
        testCategory2.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory2);

        mockMvc.perform(delete("/transactions/"+tx1.getId()+ "/delete")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction deleted successfully"));

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 + 200 income - 200 rollback = 1000
        assert (updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)) == 0);
        Assertions.assertFalse(updatedAccount.getIncomingTransactions().contains(tx1));

        Ledger updatedLedger = ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertFalse(updatedLedger.getTransactions().contains(tx1));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1000)));

        LedgerCategory updatedCategory=ledgerCategoryRepository.findById(testCategory2.getId()).orElse(null);
        Assertions.assertFalse(updatedCategory.getTransactions().contains(tx1));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteTransfer() throws Exception {
        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        Transaction tx1 = new Transfer(
                LocalDate.now(),
                null,
                testAccount,
                testAccount2,
                BigDecimal.valueOf(300),
                testLedger1
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger1);

        mockMvc.perform(delete("/transactions/" + tx1.getId() + "/delete")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Transaction deleted successfully"));

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 300 transfer + 300 rollback = 1000
        assert (updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(1000)) == 0);
        Assertions.assertFalse(updatedAccount1.getOutgoingTransactions().contains(tx1));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 300 transfer - 300 rollback = 500
        assert (updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(500)) == 0);
        Assertions.assertFalse(updatedAccount2.getIncomingTransactions().contains(tx1));

        Ledger updatedLedger = ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertFalse(updatedLedger.getTransactions().contains(tx1));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1500)));

        LedgerCategory updatedCategory=ledgerCategoryRepository.findById(testCategory2.getId()).orElse(null);
        Assertions.assertFalse(updatedCategory.getTransactions().contains(tx1));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditExpense_changeAmountOnly() throws Exception {
        Transaction tx1=new Expense(
                LocalDate.now(),
                BigDecimal.valueOf(100),
                null,
                testAccount,
                testLedger1,
                testCategory1
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory1.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory1);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Expense")
                        .param("amount", "150.00"))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Expense", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(150)));
        Assertions.assertEquals(testAccount.getId(), updatedTx.getFromAccount().getId());

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 100 expense + 100 rollback - 150 new expense = 850
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850)));


        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(850)));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditExpense_changeAccountAndAmount() throws Exception {
        Transaction tx1=new Expense(
                LocalDate.now(),
                BigDecimal.valueOf(100),
                null,
                testAccount,
                testLedger1,
                testCategory1
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory1.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory1);

        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Expense")
                        .param("amount", "150.00")
                        .param("fromAccountId", String.valueOf(testAccount2.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Expense", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(150)));

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 100 expense + 100 rollback = 1000
        assert (updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(1000)) == 0);
        Assertions.assertFalse(updatedAccount1.getOutgoingTransactions().contains(tx1));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 - 150 new expense = 350
        assert (updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(350)) == 0);
        Assertions.assertTrue(updatedAccount2.getOutgoingTransactions().contains(updatedTx));
        Assertions.assertEquals(updatedAccount2.getId(), updatedTx.getFromAccount().getId());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditExpense_changeAccountOnly() throws Exception {
        Transaction tx1=new Expense(
                LocalDate.now(),
                BigDecimal.valueOf(100),
                null,
                testAccount,
                testLedger1,
                testCategory1
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory1.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory1);

        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Expense")
                        .param("fromAccountId", String.valueOf(testAccount2.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        assert (updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)) == 0);
        Assertions.assertFalse(updatedAccount.getOutgoingTransactions().contains(tx1));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        assert (updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(400)) == 0);
        Assertions.assertTrue(updatedAccount2.getOutgoingTransactions().contains(updatedTx));

        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Expense", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(100)));
        Assertions.assertEquals(testAccount2.getId(), updatedTx.getFromAccount().getId());

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditIncome_changeAmountOnly() throws Exception {
        Transaction tx1=new Income(
                LocalDate.now(),
                BigDecimal.valueOf(200),
                null,
                testAccount,
                testLedger1,
                testCategory2
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory2.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory2);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Income")
                        .param("amount", "250.00"))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Income", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(250)));

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 + 200 income - 200 rollback + 250 new
        assert (updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1250)) == 0);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.calculateTotalAssets().compareTo(BigDecimal.valueOf(1250)));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditIncome_changeAccountAndAmount() throws Exception {
        Transaction tx1=new Income(
                LocalDate.now(),
                BigDecimal.valueOf(200),
                null,
                testAccount,
                testLedger1,
                testCategory2
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory2.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory2);

        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Income")
                        .param("amount", "250.00")
                        .param("toAccountId", String.valueOf(testAccount2.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Income", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(250)));

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 + 200 income - 200 rollback = 1000
        Assertions.assertEquals(0, updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertFalse(updatedAccount1.getIncomingTransactions().contains(tx1));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 250 new income = 750
        Assertions.assertEquals(0, updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(750)));
        Assertions.assertTrue(updatedAccount2.getIncomingTransactions().contains(updatedTx));
        Assertions.assertEquals(updatedAccount2.getId(), updatedTx.getToAccount().getId());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditIncome_changeAccountOnly() throws Exception {
        Transaction tx1=new Income(
                LocalDate.now(),
                BigDecimal.valueOf(200),
                null,
                testAccount,
                testLedger1,
                testCategory2
        );
        transactionRepository.save(tx1);

        testAccount.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        testCategory2.getTransactions().add(tx1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(testCategory2);

        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Income")
                        .param("toAccountId", String.valueOf(testAccount2.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Income", updatedTx.getNote());

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));
        Assertions.assertFalse(updatedAccount.getIncomingTransactions().contains(tx1));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(700)));
        Assertions.assertTrue(updatedAccount2.getIncomingTransactions().contains(updatedTx));
        Assertions.assertEquals(updatedAccount2.getId(), updatedTx.getToAccount().getId());

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditTransfer_changeAmountOnly() throws Exception {
        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        Transaction tx1 = new Transfer(
                LocalDate.now(),
                null,
                testAccount,
                testAccount2,
                BigDecimal.valueOf(300),
                testLedger1
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger1);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Transfer")
                        .param("amount", "350.00"))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Transfer", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(350)));

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 300 transfer + 300 rollback - 350 new transfer = 650
        Assertions.assertEquals(0, updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(650)));
        Assertions.assertTrue(updatedAccount1.getOutgoingTransactions().contains(updatedTx));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 300 transfer - 300 rollback + 350 new transfer = 850
        Assertions.assertEquals(0, updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(850)));
        Assertions.assertTrue(updatedAccount2.getIncomingTransactions().contains(updatedTx));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditTransfer_changeAccountsAndAmount() throws Exception {
        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        Transaction tx1 = new Transfer(
                LocalDate.now(),
                null,
                testAccount, //from
                testAccount2, //to
                BigDecimal.valueOf(300),
                testLedger1
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger1);

        BasicAccount testAccount3 = new BasicAccount("Test Account 3",
                BigDecimal.valueOf(800),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount3);
        testUser.getAccounts().add(testAccount3);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("date", "2025-11-01")
                        .param("note", "Updated Transfer")
                        .param("amount", "350.00")
                        .param("fromAccountId", String.valueOf(testAccount3.getId()))
                        .param("toAccountId", String.valueOf(testAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(LocalDate.of(2025,11,1), updatedTx.getDate());
        Assertions.assertEquals("Updated Transfer", updatedTx.getNote());
        Assertions.assertEquals(0, updatedTx.getAmount().compareTo(BigDecimal.valueOf(350)));
        Assertions.assertEquals(testAccount3.getId(), updatedTx.getFromAccount().getId());
        Assertions.assertEquals(testAccount.getId(), updatedTx.getToAccount().getId());

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 300 transfer + 300 rollback + 350 new transfer = 1350
        Assertions.assertEquals(0, updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(1350)));
        Assertions.assertTrue(updatedAccount1.getIncomingTransactions().contains(updatedTx));
        Assertions.assertFalse(updatedAccount1.getOutgoingTransactions().contains(updatedTx));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 300 transfer - 300 rollback = 500
        Assertions.assertEquals(0, updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(500)));
        Assertions.assertFalse(updatedAccount2.getIncomingTransactions().contains(updatedTx));
        Assertions.assertFalse(updatedAccount2.getOutgoingTransactions().contains(updatedTx));

        Account updatedAccount3 = accountRepository.findById(testAccount3.getId()).orElse(null);
        // Initial balance 800 - 350 new transfer = 450
        Assertions.assertEquals(0, updatedAccount3.getBalance().compareTo(BigDecimal.valueOf(450)));
        Assertions.assertTrue(updatedAccount3.getOutgoingTransactions().contains(updatedTx));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditTransfer_changeAccountsOnly() throws Exception {
        BasicAccount testAccount2 = new BasicAccount("Test Account 2",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

        Transaction tx1 = new Transfer(
                LocalDate.now(),
                null,
                testAccount, //from
                testAccount2, //to
                BigDecimal.valueOf(300),
                testLedger1
        );
        transactionRepository.save(tx1);
        testAccount.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger1.getTransactions().add(tx1);
        accountRepository.save(testAccount);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger1);

        BasicAccount testAccount3 = new BasicAccount("Test Account 3",
                BigDecimal.valueOf(800),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount3);
        testUser.getAccounts().add(testAccount3);
        userRepository.save(testUser);

        mockMvc.perform(put("/transactions/"+tx1.getId()+ "/edit")
                        .principal(() -> "Alice")
                        .param("transactionId", String.valueOf(tx1.getId()))
                        .param("fromAccountId", String.valueOf(testAccount3.getId()))
                        .param("toAccountId", String.valueOf(testAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Edited successfully"));

        Transaction updatedTx = transactionRepository.findById(tx1.getId()).orElse(null);
        Assertions.assertEquals(testAccount3.getId(), updatedTx.getFromAccount().getId());
        Assertions.assertEquals(testAccount.getId(), updatedTx.getToAccount().getId());

        Account updatedAccount1 = accountRepository.findById(testAccount.getId()).orElse(null);
        // Initial balance 1000 - 300 transfer + 300 rollback + 300 new transfer = 1300
        Assertions.assertEquals(0, updatedAccount1.getBalance().compareTo(BigDecimal.valueOf(1300)));
        Assertions.assertTrue(updatedAccount1.getIncomingTransactions().contains(updatedTx));
        Assertions.assertFalse(updatedAccount1.getOutgoingTransactions().contains(updatedTx));

        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        // Initial balance 500 + 300 transfer - 300 rollback = 500
        Assertions.assertEquals(0, updatedAccount2.getBalance().compareTo(BigDecimal.valueOf(500)));
        Assertions.assertFalse(updatedAccount2.getIncomingTransactions().contains(updatedTx));
        Assertions.assertFalse(updatedAccount2.getOutgoingTransactions().contains(updatedTx));

        Account updatedAccount3 = accountRepository.findById(testAccount3.getId()).orElse(null);
        // Initial balance 800 - 300 new transfer = 500
        Assertions.assertEquals(0, updatedAccount3.getBalance().compareTo(BigDecimal.valueOf(500)));
        Assertions.assertTrue(updatedAccount3.getOutgoingTransactions().contains(updatedTx));
    }
}
