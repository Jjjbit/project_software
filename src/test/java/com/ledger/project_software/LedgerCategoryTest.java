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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class LedgerCategoryTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    private Ledger testLedger1;
    private Ledger testLedger2;
    private User testUser;
    private Account testAccount;

    @BeforeEach
    public void setUp(){
        testUser=new User("Alice", "pass123");
        userRepository.save(testUser);

        testLedger1 = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger1);
        testLedger2=new Ledger("Test Ledger2", testUser);
        ledgerRepository.save(testLedger2);

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
        userRepository.save(testUser);
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateCategory() throws Exception{
        mockMvc.perform(post("/ledger-categories/create-category")
                        .principal(() -> "Alice")
                        .param("ledgerId", String.valueOf(testLedger1.getId()))
                        .param("name", "Food")
                        .param("type", String.valueOf(CategoryType.EXPENSE)))
                .andExpect(status().isOk())
                .andExpect(content().string("Category created successfully"));

        LedgerCategory createdCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertNotNull(createdCategory);
        Assertions.assertEquals(CategoryType.EXPENSE, createdCategory.getType());
        Assertions.assertEquals(testLedger1.getId(), createdCategory.getLedger().getId());

        Ledger updatedLedger=ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertTrue(updatedLedger.getCategories().contains(createdCategory));
        Assertions.assertEquals(1, updatedLedger.getCategories().size());

        Ledger notUpdatedLedger=ledgerRepository.findById(testLedger2.getId()).orElse(null);
        Assertions.assertFalse(notUpdatedLedger.getCategories().contains(createdCategory));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateSubCategory() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        mockMvc.perform(post("/ledger-categories/" + foodCategory.getId() + "/create-subcategory")
                        .principal(() -> "Alice")
                        .param("ledgerId", String.valueOf(testLedger1.getId()))
                        .param("name", "lunch")
                        .param("type", String.valueOf(CategoryType.EXPENSE)))
                .andExpect(status().isOk())
                .andExpect(content().string("SubCategory created successfully"));

        LedgerCategory createdSubCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "lunch");
        Assertions.assertNotNull(createdSubCategory);
        Assertions.assertEquals(CategoryType.EXPENSE, createdSubCategory.getType());
        Assertions.assertEquals(testLedger1.getId(), createdSubCategory.getLedger().getId());
        Assertions.assertEquals(foodCategory.getId(), createdSubCategory.getParent().getId());

        LedgerCategory updatedFoodCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertTrue(updatedFoodCategory.getChildren().contains(createdSubCategory));
        Assertions.assertEquals(1, updatedFoodCategory.getChildren().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedgerCategory_WithoutTransactions() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(mealsCategory);

        LedgerCategory lunch=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        mealsCategory.getChildren().add(lunch);
        lunch.setParent(mealsCategory);
        ledgerCategoryRepository.save(lunch);

        testLedger1.getCategories().add(foodCategory);
        testLedger1.getCategories().add(mealsCategory);
        testLedger1.getCategories().add(lunch);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        foodCategory.getTransactions().add(transaction1);

        //test delete subcategory with budget
        Budget budget1=new Budget(BigDecimal.valueOf(100), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(budget1);
        foodCategory.getBudgets().add(budget1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);


        mockMvc.perform(delete("/ledger-categories/"+ foodCategory.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Must provide migrateToCategoryId"));

        mockMvc.perform(delete("/ledger-categories/"+ foodCategory.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false")
                        .param("migrateToCategoryId", String.valueOf(lunch.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("migrateToCategory must be a Category"));


        mockMvc.perform(delete("/ledger-categories/"+ foodCategory.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false")
                        .param("migrateToCategoryId",  String.valueOf(mealsCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted successfully"));

        Ledger updateLedger = ledgerRepository.findById(testLedger1.getId()).orElseThrow();
        Assertions.assertEquals(1, updateLedger.getTransactions().size());

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertNull(updateFoodCategory);

        LedgerCategory updateMealsCategory=ledgerCategoryRepository.findById(mealsCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updateMealsCategory.getTransactions().size());

        Account updateAccount=accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(BigDecimal.valueOf(990)));

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(990)));

        Budget updateBudget=budgetRepository.findById(budget1.getId()).orElse(null);
        Assertions.assertNull(updateBudget);
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedgerCategory_WithChild() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory lunch=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        foodCategory.getChildren().add(lunch);
        lunch.setParent(foodCategory);
        ledgerCategoryRepository.save(lunch);

        testLedger1.getCategories().add(foodCategory);
        testLedger1.getCategories().add(lunch);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        foodCategory.getTransactions().add(transaction1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);

        mockMvc.perform(delete("/ledger-categories/"+ foodCategory.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot delete category with subcategories"));
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedgerCategory_WithTransactions() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(foodCategory);
        testLedger1.getCategories().add(foodCategory);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        foodCategory.getTransactions().add(transaction1);

        //test delete category with budget
        Budget budget1=new Budget(BigDecimal.valueOf(100), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(budget1);
        foodCategory.getBudgets().add(budget1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);

        mockMvc.perform(delete("/ledger-categories/"+ foodCategory.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "true")
                        .param("migrateToCategoryId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted successfully"));

        Ledger updateLedger = ledgerRepository.findById(testLedger1.getId()).orElseThrow();
        Assertions.assertEquals(0, updateLedger.getTransactions().size());

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertNull(updateFoodCategory);

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(BigDecimal.valueOf(1000)));

        Account updateAccount=accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(BigDecimal.valueOf(1000)));

        Budget updateBudget=budgetRepository.findById(budget1.getId()).orElse(null);
        Assertions.assertNull(updateBudget);
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedgerSubCategory_WithTransactions() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(foodCategory);
        testLedger1.getCategories().add(foodCategory);

        LedgerCategory lunch=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        foodCategory.getChildren().add(lunch);
        lunch.setParent(foodCategory);
        ledgerCategoryRepository.save(lunch);
        testLedger1.getCategories().add(lunch);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, lunch);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        lunch.getTransactions().add(transaction1);

        //test delete subcategory with budget
        Budget budget1=new Budget(BigDecimal.valueOf(100), Budget.Period.MONTHLY, lunch, testUser);
        budgetRepository.save(budget1);
        lunch.getBudgets().add(budget1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(lunch);

        mockMvc.perform(delete("/ledger-categories/"+ lunch.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "true")
                        .param("migrateToCategoryId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted successfully"));

        Ledger updateLedger = ledgerRepository.findById(testLedger1.getId()).orElseThrow();
        Assertions.assertEquals(0, updateLedger.getTransactions().size());

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updateFoodCategory.getChildren().size());
        Assertions.assertEquals(0, updateFoodCategory.getTransactions().size());

        LedgerCategory updateLunch=ledgerCategoryRepository.findById(lunch.getId()).orElse(null);
        Assertions.assertNull(updateLunch);

        Account updateAccount=accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(new BigDecimal("1000")));

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(new BigDecimal("1000")));

        Budget updateBudget=budgetRepository.findById(budget1.getId()).orElse(null);
        Assertions.assertNull(updateBudget);
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteLedgerSubCategory_WithoutTransactions() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(mealsCategory);

        LedgerCategory lunch=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        foodCategory.getChildren().add(lunch);
        lunch.setParent(foodCategory);
        ledgerCategoryRepository.save(lunch);

        //test delete subcategory with transactions migrated to another Subcategory
        LedgerCategory snacks=new LedgerCategory("Snacks", CategoryType.EXPENSE, testLedger1);
        foodCategory.getChildren().add(snacks);
        snacks.setParent(foodCategory);
        ledgerCategoryRepository.save(snacks);

        testLedger1.getCategories().add(foodCategory);
        testLedger1.getCategories().add(mealsCategory);
        testLedger1.getCategories().add(lunch);
        testLedger1.getCategories().add(snacks);

        //test delete subcategory with transactions migrated to another category
        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, lunch);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        lunch.getTransactions().add(transaction1);

        //test delete subcategory with budget
        Budget budget1=new Budget(BigDecimal.valueOf(100), Budget.Period.MONTHLY, lunch, testUser);
        budgetRepository.save(budget1);
        lunch.getBudgets().add(budget1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(lunch);

        mockMvc.perform(delete("/ledger-categories/"+ lunch.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false")
                        .param("migrateToCategoryId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Must provide migrateToCategoryId"));

        mockMvc.perform(delete("/ledger-categories/"+ lunch.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false")
                        .param("migrateToCategoryId", String.valueOf(snacks.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("migrateToCategory must be a Category"));

        mockMvc.perform(delete("/ledger-categories/"+ lunch.getId() +"/delete")
                        .principal(() -> "Alice")
                        .param("deleteTransactions", "false")
                        .param("migrateToCategoryId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted successfully"));

        Ledger updateLedger = ledgerRepository.findById(testLedger1.getId()).orElseThrow();
        Assertions.assertEquals(1, updateLedger.getTransactions().size());

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updateFoodCategory.getChildren().size());
        Assertions.assertEquals(0, updateFoodCategory.getTransactions().size());

        LedgerCategory updateLunch=ledgerCategoryRepository.findById(lunch.getId()).orElse(null);
        Assertions.assertNull(updateLunch);

        LedgerCategory updateMealsCategory=ledgerCategoryRepository.findById(mealsCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updateMealsCategory.getTransactions().size());

        Account updateAccount=accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updateAccount.getBalance().compareTo(new BigDecimal("990")));

        User updateUser=userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updateUser.getTotalAssets().compareTo(new BigDecimal("990")));

        Budget updateBudget=budgetRepository.findById(budget1.getId()).orElse(null);
        Assertions.assertNull(updateBudget);
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testRename() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory travelCategory=new LedgerCategory("Travel", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(travelCategory);
        ledgerCategoryRepository.save(travelCategory);

        mockMvc.perform(put("/ledger-categories/"+foodCategory.getId()+"/rename")
                        .param("newName", "Food")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Renamed successfully"));

        mockMvc.perform(put("/ledger-categories/"+foodCategory.getId() + "/rename")
                        .param("newName", "Travel")
                        .principal(() -> "Alice"))
                .andExpect(status().isConflict())
                .andExpect(content().string("new name exists already"));

        mockMvc.perform(put("/ledger-categories/"+foodCategory.getId() + "/rename")
                        .param("newName", "food")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Renamed successfully"));

        LedgerCategory updateCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "food");
        Assertions.assertNotNull(updateCategory);


        mockMvc.perform(put("/ledger-categories/"+foodCategory.getId() +"/rename")
                        .param("newName", "")
                        .principal(() -> "Alice"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("new name cannot be null"));
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testPromote() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        mealsCategory.setParent(foodCategory);
        foodCategory.getChildren().add(mealsCategory);
        testLedger1.getCategories().add(mealsCategory);
        ledgerCategoryRepository.save(mealsCategory);
        ledgerCategoryRepository.save(foodCategory);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, mealsCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        mealsCategory.getTransactions().add(transaction1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(mealsCategory);

        mockMvc.perform(put("/ledger-categories/"+ mealsCategory.getId() +"/promote")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Promoted successfully"));

        LedgerCategory updatedMealsCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Meals");
        Assertions.assertNull(updatedMealsCategory.getParent());

        LedgerCategory updatedFoodCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertEquals(0, updatedFoodCategory.getChildren().size());
        Assertions.assertEquals(0, updatedFoodCategory.getTransactions().size());

        Ledger updatedLedger=ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getCategories().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDemote() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(mealsCategory);
        ledgerCategoryRepository.save(mealsCategory);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        foodCategory.getTransactions().add(transaction1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);


        mockMvc.perform(put("/ledger-categories/"+ foodCategory.getId() +"/demote")
                        .principal(() -> "Alice")
                        .param("parentId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Demoted successfully"));

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertEquals(mealsCategory.getId(), updateFoodCategory.getParent().getId());


        LedgerCategory updateMealsCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Meals");
        Assertions.assertEquals(1, updateMealsCategory.getChildren().size());
        Assertions.assertEquals(1, updateMealsCategory.getTransactions().size());
        Assertions.assertTrue(updateMealsCategory.getTransactions().contains(transaction1));

        Ledger updatedLedger=ledgerRepository.findById(testLedger1.getId()).orElse(null);
        Assertions.assertEquals(2, updatedLedger.getCategories().size());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDemote_WithSubCategory() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(mealsCategory);
        ledgerCategoryRepository.save(mealsCategory);

        LedgerCategory lunchCategory=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        lunchCategory.setParent(foodCategory);
        foodCategory.getChildren().add(lunchCategory);
        ledgerCategoryRepository.save(lunchCategory);
        ledgerCategoryRepository.save(foodCategory);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        Transaction transaction2 = new Expense(LocalDate.now(), BigDecimal.valueOf(15),null, testAccount, testLedger1, lunchCategory);
        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);
        testAccount.addTransaction(transaction1);
        testAccount.addTransaction(transaction2);
        testLedger1.getTransactions().add(transaction1);
        testLedger1.getTransactions().add(transaction2);
        foodCategory.getTransactions().add(transaction1);
        lunchCategory.getTransactions().add(transaction2);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(lunchCategory);

        mockMvc.perform(put("/ledger-categories/"+ foodCategory.getId() +"/demote")
                        .principal(() -> "Alice")
                        .param("parentId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot demote category with subcategories"));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testChangeParent() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory lunch=new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger1);
        ledgerCategoryRepository.save(lunch);
        foodCategory.getChildren().add(lunch);
        lunch.setParent(foodCategory);
        testLedger1.getCategories().add(lunch);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, lunch);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.getTransactions().add(transaction1);
        lunch.getTransactions().add(transaction1);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        testLedger1.getCategories().add(mealsCategory);
        ledgerCategoryRepository.save(mealsCategory);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(lunch);

        mockMvc.perform(put("/ledger-categories/"+ foodCategory.getId() +"/change-parent")
                        .principal(() -> "Alice")
                        .param("newParentId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Must be a SubCategory"));

        mockMvc.perform(put("/ledger-categories/"+ lunch.getId() +"/change-parent")
                        .principal(() -> "Alice")
                        .param("newParentId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Parent category changed successfully"));

        LedgerCategory updateLunch=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Lunch");
        Assertions.assertEquals(mealsCategory.getId(), updateLunch.getParent().getId());

        LedgerCategory updateMealsCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Meals");
        Assertions.assertEquals(1, updateMealsCategory.getChildren().size());
        Assertions.assertEquals(1, updateMealsCategory.getTransactions().size());

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertEquals(0, updateFoodCategory.getChildren().size());

    }

}
