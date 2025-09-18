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
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class LedgerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    private User testUser;
    private BasicAccount testAccount1;
    private BasicAccount testAccount2;

    @BeforeEach
    public void setUp() {
        testUser = new User("Alice", "pass123");
        userRepository.save(testUser);

        testAccount1 = new BasicAccount("test Account 1", BigDecimal.valueOf(1000), null, true, true, AccountType.CASH, AccountCategory.FUNDS, testUser);
        testAccount2 = new BasicAccount("test Account 2", BigDecimal.valueOf(2000), null, true, true, AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser);
        accountRepository.save(testAccount1);
        accountRepository.save(testAccount2);
        testUser.getAccounts().add(testAccount1);
        testUser.getAccounts().add(testAccount2);
        userRepository.save(testUser);

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateLedger() throws Exception {
        mockMvc.perform(post("/ledgers/create")
                        .principal(() -> "Alice")
                        .param("name", "My Ledger"))
                .andExpect(status().isOk())
                .andExpect(content().string("ledger created successfully"));

        Ledger updateLedger = ledgerRepository.findByName("My Ledger");
        Assertions.assertNotNull(updateLedger);
        Assertions.assertEquals("My Ledger", updateLedger.getName());
        Assertions.assertEquals(15, updateLedger.getCategories().size());


        // Try to create a ledger with the same name
        mockMvc.perform(post("/ledgers/create")
                        .principal(() -> "Alice")
                        .param("name", "My Ledger"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Ledger name already exists"));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedger() throws Exception {
        Ledger ledger = new Ledger("To Be Deleted", testUser);
        ledgerRepository.save(ledger);
        testUser.getLedgers().add(ledger);

        LedgerCategory testCategory = new LedgerCategory("Test Category", CategoryType.EXPENSE, ledger);
        LedgerCategory testCategory2 = new LedgerCategory("Test Category 2", CategoryType.INCOME, ledger);
        ledger.getCategories().add(testCategory);
        ledger.getCategories().add(testCategory2);
        ledgerCategoryRepository.saveAll(List.of(testCategory, testCategory2));


        Transaction transaction1 = new Transfer(LocalDate.now(), null, testAccount1, testAccount2, BigDecimal.valueOf(100), ledger);
        Transaction transaction2 = new Transfer(LocalDate.now(), null, testAccount1, null, BigDecimal.valueOf(200), ledger);
        Transaction transaction3 = new Transfer(LocalDate.now(), null, null, testAccount2, BigDecimal.valueOf(300), ledger);
        Transaction transaction4 = new Expense(LocalDate.now(), BigDecimal.valueOf(500), null, testAccount1,ledger, testCategory);
        Transaction transaction5 = new Income(LocalDate.now(), BigDecimal.valueOf(1000), null, testAccount2, ledger, testCategory2);
        transactionRepository.saveAll(List.of(transaction1, transaction2, transaction3, transaction4, transaction5));

        // link transactions to ledger
        ledger.getTransactions().addAll(List.of(transaction1, transaction2, transaction3, transaction4, transaction5));

        // link transactions to accounts
        testAccount1.addTransaction(transaction1);
        testAccount1.addTransaction(transaction2);
        testAccount1.addTransaction(transaction4);
        testAccount2.addTransaction(transaction1);
        testAccount2.addTransaction(transaction3);
        testAccount2.addTransaction(transaction5);

        // link transactions to categories
        testCategory.getTransactions().add(transaction4);
        testCategory2.getTransactions().add(transaction5);

        //category with budget
        Budget budget = new Budget(BigDecimal.valueOf(1000), Budget.Period.MONTHLY, testCategory, testUser);
        testCategory.getBudgets().add(budget);
        budgetRepository.save(budget);

        userRepository.save(testUser);
        ledgerRepository.save(ledger);
        ledgerCategoryRepository.saveAll(List.of(testCategory, testCategory2));
        transactionRepository.saveAll(List.of(transaction1, transaction2, transaction3, transaction4, transaction5));
        accountRepository.saveAll(List.of(testAccount1, testAccount2));


        mockMvc.perform(delete("/ledgers/"+ ledger.getId() +"/delete")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ledger deleted successfully"));


        Ledger deletedLedger = ledgerRepository.findById(ledger.getId()).orElse(null);
        Assertions.assertNull(deletedLedger);

        // Check that transactions are deleted
        Assertions.assertEquals(0, transactionRepository.findAll().size());

        Account updatedAccount1 = accountRepository.findById(testAccount1.getId()).orElse(null);
        Account updatedAccount2 = accountRepository.findById(testAccount2.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount1.getTransactions().size());
        Assertions.assertEquals(0, updatedAccount2.getTransactions().size());

        // Check that categories are deleted
        LedgerCategory updatedCategory1 = ledgerCategoryRepository.findById(testCategory.getId()).orElse(null);
        LedgerCategory updatedCategory2 = ledgerCategoryRepository.findById(testCategory2.getId()).orElse(null);
        Assertions.assertNull(updatedCategory1);
        Assertions.assertNull(updatedCategory2);

        // Check that budget is deleted
        Budget updatedBudget = budgetRepository.findById(budget.getId()).orElse(null);
        Assertions.assertNull(updatedBudget);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updatedUser.getLedgers().size()); // only default ledger remains
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCopyLedger() throws Exception {
        Ledger ledger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(ledger);
        testUser.getLedgers().add(ledger);

        LedgerCategory testCategory = new LedgerCategory("Test Category", CategoryType.EXPENSE, ledger);
        LedgerCategory testCategory2 = new LedgerCategory("Test Category 2", CategoryType.INCOME, ledger);
        ledger.getCategories().add(testCategory);
        ledger.getCategories().add(testCategory2);
        ledgerCategoryRepository.saveAll(List.of(testCategory, testCategory2));

        mockMvc.perform(post("/ledgers/" + ledger.getId() + "/copy")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("copy ledger"));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(3, updatedUser.getLedgers().size()); // original + copy+default

        Ledger copiedLedger = ledgerRepository.findByName(ledger.getName() + " Copy");
        Assertions.assertNotNull(copiedLedger);
        Assertions.assertEquals(2, copiedLedger.getCategories().size());

        LedgerCategory copiedCategory1 = ledgerCategoryRepository.findByLedgerAndName(copiedLedger, "Test Category");
        LedgerCategory copiedCategory2 = ledgerCategoryRepository.findByLedgerAndName(copiedLedger, "Test Category 2");
        Assertions.assertNotNull(copiedCategory1);
        Assertions.assertNotNull(copiedCategory2);


    }

    @Test
    @WithMockUser(username = "Alice")
    public void testRenameLedger() throws Exception {
        Ledger ledger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(ledger);
        testUser.getLedgers().add(ledger);

        Ledger anotherLedger = new Ledger("Another Ledger", testUser);
        ledgerRepository.save(anotherLedger);
        testUser.getLedgers().add(anotherLedger);

        mockMvc.perform(put("/ledgers/" + ledger.getId() + "/rename")
                        .principal(() -> "Alice")
                        .param("newName", "Another Ledger"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Ledger name already exists"));

        mockMvc.perform(put("/ledgers/9999/rename")
                        .principal(() -> "Alice")
                        .param("newName", "new name"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Ledger not found"));

        mockMvc.perform(put("/ledgers/" + ledger.getId() + "/rename")
                        .principal(() -> "Alice")
                        .param("newName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("ledger name cannot be empty"));

        mockMvc.perform(put("/ledgers/" + ledger.getId() + "/rename")
                        .principal(() -> "Alice")
                        .param("newName", "Renamed Ledger"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ledger renamed successfully"));

        Ledger renamedLedger = ledgerRepository.findById(ledger.getId()).orElse(null);
        Assertions.assertEquals("Renamed Ledger", renamedLedger.getName());
    }

}
