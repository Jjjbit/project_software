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
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

    @Configuration //converte String in YearMonth per i test
    public class WebConfig implements WebMvcConfigurer {

        @Override
        public void addFormatters(FormatterRegistry registry) {
            registry.addConverter(new Converter<String, YearMonth>() {
                @Override
                public YearMonth convert(String source) {
                    return YearMonth.parse(source, DateTimeFormatter.ofPattern("yyyy-MM"));
                }
            });
        }
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
        Assertions.assertEquals(0, updatedUser.getLedgers().size());
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
        Assertions.assertEquals(2, updatedUser.getLedgers().size());

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


    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllLedgers() throws Exception {
        Ledger testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);
        Ledger testLedger1 = new Ledger("Another Ledger", testUser);
        ledgerRepository.save(testLedger1);
        testUser.getLedgers().add(testLedger1);

        mockMvc.perform(get("/ledgers/all-ledgers")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Test Ledger"))
                .andExpect(jsonPath("$[1].name").value("Another Ledger"));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetLedgerTransactionsForMonth_WithMonth() throws Exception {
        Ledger testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        Transaction tx1 = new Transfer(LocalDate.of(2025, 10, 5),
                null,
                testAccount1,
                testAccount2,
                BigDecimal.valueOf(100),
                testLedger
        );
        transactionRepository.save(tx1);
        testAccount1.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger.getTransactions().add(tx1);

        Transaction tx2 = new Transfer(LocalDate.of(2025, 10, 5),
                null,
                testAccount2,
                testAccount1,
                BigDecimal.valueOf(50),
                testLedger
        );
        transactionRepository.save(tx2);
        testAccount1.addTransaction(tx2);
        testAccount2.addTransaction(tx2);
        testLedger.getTransactions().add(tx2);

        accountRepository.save(testAccount1);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger);

        mockMvc.perform(get("/ledgers/{ledgerId}/all-transactions-for-month", testLedger.getId())
                        .principal(() -> "Alice")
                        .param("month", "2025-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].amount").value(50));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetLedgerTransactionsForMonth_WithoutMonth() throws Exception {
        Ledger testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        Transaction tx1 = new Transfer(LocalDate.now(),
                null,
                testAccount1,
                testAccount2,
                BigDecimal.valueOf(100),
                testLedger
        );
        transactionRepository.save(tx1);
        testAccount1.addTransaction(tx1);
        testAccount2.addTransaction(tx1);
        testLedger.getTransactions().add(tx1);

        Transaction tx2 = new Transfer(LocalDate.now(),
                null,
                testAccount2,
                testAccount1,
                BigDecimal.valueOf(50),
                testLedger
        );
        transactionRepository.save(tx2);
        testAccount1.addTransaction(tx2);
        testAccount2.addTransaction(tx2);
        testLedger.getTransactions().add(tx2);

        accountRepository.save(testAccount1);
        accountRepository.save(testAccount2);
        ledgerRepository.save(testLedger);

        mockMvc.perform(get("/ledgers/{ledgerId}/all-transactions-for-month", testLedger.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].amount").value(50));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetLedgerCategories() throws Exception {
        Ledger testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        LedgerCategory foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(foodCategory);
        testLedger.getCategories().add(foodCategory);

        LedgerCategory transportCategory = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(transportCategory);
        testLedger.getCategories().add(transportCategory);

        LedgerCategory lunchCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(lunchCategory);
        testLedger.getCategories().add(lunchCategory);
        lunchCategory.setParent(foodCategory);
        foodCategory.getChildren().add(lunchCategory);

        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);
        ledgerCategoryRepository.save(lunchCategory);
        ledgerRepository.save(testLedger);


        mockMvc.perform(get("/ledgers/{ledgerId}/categories", testLedger.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].CategoryName").value("Food"))
                .andExpect(jsonPath("$.categories[1].CategoryName").value("Transport"))
                .andExpect(jsonPath("$.categories[0].subCategories[0].SubCategoryName").value("Lunch"));;
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetMonthlySummary() throws Exception {
        Ledger testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        Transaction tx1=new Expense(LocalDate.of(2025,6,5), BigDecimal.valueOf(320), null, testAccount1,testLedger,null);
        transactionRepository.save(tx1);
        testLedger.getTransactions().add(tx1);
        testAccount1.addTransaction(tx1);

        Transaction tx2=new Income(LocalDate.of(2025,6,5), BigDecimal.valueOf(500), null, testAccount2,testLedger,null);
        transactionRepository.save(tx2);
        testLedger.getTransactions().add(tx2);
        testAccount2.addTransaction(tx2);


        Transaction tx3=new Expense(LocalDate.now(), BigDecimal.valueOf(320), null, testAccount1,testLedger,null);
        transactionRepository.save(tx3);
        testLedger.getTransactions().add(tx3);
        testAccount1.addTransaction(tx3);

        Transaction tx4=new Income(LocalDate.now(), BigDecimal.valueOf(500), null, testAccount2,testLedger,null);
        transactionRepository.save(tx4);
        testLedger.getTransactions().add(tx4);
        testAccount2.addTransaction(tx4);


        mockMvc.perform(get("/ledgers/{ledgerId}/monthly-summary", testLedger.getId())
                        .param("month", "2025-06")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.month").value("2025-06"))
                .andExpect(jsonPath("$.totalIncome").value(500))
                .andExpect(jsonPath("$.totalExpense").value(320));

        mockMvc.perform(get("/ledgers/{ledgerId}/monthly-summary", testLedger.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.month").value(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))))
                .andExpect(jsonPath("$.totalIncome").value(500))
                .andExpect(jsonPath("$.totalExpense").value(320));
    }

}
