package com.ledger.project_software;

import com.ledger.project_software.orm.*;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class BudgetTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetDAO budgetRepository;

    @Autowired
    private UserDAO userRepository;

    @Autowired
    private LedgerCategoryDAO ledgerCategoryRepository;

    @Autowired
    private LedgerDAO ledgerRepository;

    @Autowired
    private AccountDAO accountRepository;

    @Autowired
    private TransactionDAO transactionRepository;

    private User testUser;
    private Ledger testLedger;
    private LedgerCategory foodCategory;
    private LedgerCategory lunch;
    private LedgerCategory transportCategory;
    private Account testAccount1;

    @BeforeEach
    public void setup() {
        testUser = new User("Alice", "password123");
        userRepository.save(testUser);

        testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

        testAccount1 = new BasicAccount("test Account 1", BigDecimal.valueOf(1000), null, true, true, AccountType.CASH, AccountCategory.FUNDS, testUser);
        accountRepository.save(testAccount1);
        testUser.getAccounts().add(testAccount1);

        foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(foodCategory);
        testLedger.getCategories().add(foodCategory);

        transportCategory = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        ledgerCategoryRepository.save(transportCategory);
        testLedger.getCategories().add(transportCategory);

        lunch = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        lunch.setParent(foodCategory);
        foodCategory.getChildren().add(lunch);
        testLedger.getCategories().add(lunch);
        ledgerCategoryRepository.save(lunch);
        ledgerCategoryRepository.save(foodCategory);

    }

    //create
    @Test
    @WithMockUser(username = "Alice")
    public void testCreateUncategorizedBudget() throws Exception {
        //create uncategorized budget
        mockMvc.perform(post("/budgets/create")
                        .param("amount", "500")
                        .principal(() -> "Alice")
                        .param("period", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget created successfully"));

        Budget createdBudget = budgetRepository.findByUserAndOptionalCategoryAndPeriod(testUser.getId(), null, Budget.Period.MONTHLY).orElse(null);
        Assertions.assertNotNull(createdBudget);
        Assertions.assertEquals(0, createdBudget.getAmount().compareTo(BigDecimal.valueOf(500)));
        Assertions.assertEquals(Budget.Period.MONTHLY, createdBudget.getPeriod());
        Assertions.assertNull(createdBudget.getCategory());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updatedUser.getBudgets().size());
        Assertions.assertEquals(0, updatedUser.getBudgets().get(0).getAmount().compareTo(BigDecimal.valueOf(500)));
        Assertions.assertEquals(Budget.Period.MONTHLY, updatedUser.getBudgets().get(0).getPeriod());
        Assertions.assertNull(updatedUser.getBudgets().get(0).getCategory());
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateCategoryBudget() throws Exception {
        //create category budget
        mockMvc.perform(post("/budgets/create")
                        .param("amount", "300")
                        .param("categoryComponentId", String.valueOf(foodCategory.getId()))
                        .param("period", "MONTHLY")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget created successfully"));

        Assertions.assertEquals(1, budgetRepository.findAll().size());

        Budget createdCategoryBudget = budgetRepository.findByUserAndOptionalCategoryAndPeriod(testUser.getId(), foodCategory.getId(), Budget.Period.MONTHLY).orElse(null);
        Assertions.assertNotNull(createdCategoryBudget);
        Assertions.assertEquals(0, createdCategoryBudget.getAmount().compareTo(BigDecimal.valueOf(300)));
        Assertions.assertEquals(Budget.Period.MONTHLY, createdCategoryBudget.getPeriod());
        Assertions.assertNotNull(createdCategoryBudget.getCategory());
        Assertions.assertEquals("Food", createdCategoryBudget.getCategory().getName());

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(1, updatedFoodCategory.getBudgets().size());


    }

    //edit
    @Test
    @WithMockUser(username = "Alice")
    public void testEditBudget() throws Exception {
        //create uncategorized budget
        Budget budget = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budgetRepository.save(budget);
        testUser.getBudgets().add(budget);
        userRepository.save(testUser);

        //edit amount
        mockMvc.perform(put("/budgets/"+ budget.getId() +"/edit")
                        .param("amount", "800")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget updated successfully"));

        Budget updatedBudget = budgetRepository.findById(budget.getId()).orElse(null);
        Assertions.assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(800)));
        Assertions.assertEquals(Budget.Period.MONTHLY, updatedBudget.getPeriod());
        Assertions.assertNull(updatedBudget.getCategory());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updatedUser.getBudgets().size());
        Assertions.assertEquals(0, updatedUser.getBudgets().get(0).getAmount().compareTo(BigDecimal.valueOf(800)));
        Assertions.assertEquals(Budget.Period.MONTHLY, updatedUser.getBudgets().get(0).getPeriod());
        Assertions.assertNull(updatedUser.getBudgets().get(0).getCategory());

        //edit category's budget
        //create another budget with category
        Budget budgetWithCategory = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                foodCategory,
                testUser);
        budgetRepository.save(budgetWithCategory);

        mockMvc.perform(put("/budgets/"+ budgetWithCategory.getId() +"/edit")
                        .param("amount", "800")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget updated successfully"));

        Budget updatedBudgetWithCategory = budgetRepository.findById(budgetWithCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedBudgetWithCategory.getAmount().compareTo(BigDecimal.valueOf(800)));
        Assertions.assertEquals(Budget.Period.MONTHLY, updatedBudgetWithCategory.getPeriod());
        Assertions.assertNotNull(updatedBudgetWithCategory.getCategory());
        Assertions.assertEquals("Food", updatedBudgetWithCategory.getCategory().getName());
    }

    //delete
    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteBudget() throws Exception {
        //delete uncategorized budget
        Budget budget = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budgetRepository.save(budget);
        testUser.getBudgets().add(budget);
        userRepository.save(testUser);

        mockMvc.perform(delete("/budgets/"+ budget.getId() +"/delete")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget deleted successfully"));

        Budget deletedBudget = budgetRepository.findById(budget.getId()).orElse(null);
        Assertions.assertNull(deletedBudget);

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getBudgets().size());
        Assertions.assertEquals(0, budgetRepository.findAll().size());

        //delete another budget with category
        Budget budgetWithCategory = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                foodCategory,
                testUser);
        budgetRepository.save(budgetWithCategory);

        mockMvc.perform(delete("/budgets/"+ budgetWithCategory.getId() +"/delete")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budget deleted successfully"));

        Budget deletedBudgetWithCategory = budgetRepository.findById(budgetWithCategory.getId()).orElse(null);
        Assertions.assertNull(deletedBudgetWithCategory);

        LedgerCategory updatedFoodCategory = ledgerCategoryRepository.findById(foodCategory.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getBudgets().size());

        Assertions.assertEquals(0, budgetRepository.findAll().size());
    }

    //merge
    @Test
    @WithMockUser(username = "Alice")
    public void testMergeBudgets() throws Exception {

        //merge category budgets to uncategorized budget
        //create uncategorized budget
        Budget budget1 = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budgetRepository.save(budget1);
        testUser.getBudgets().add(budget1);

        //crate category budget
        Budget foodBudget = new Budget(
                BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                foodCategory,
                testUser);
        budgetRepository.save(foodBudget);
        foodCategory.getBudgets().add(foodBudget);

        Budget transportBudget = new Budget(
                BigDecimal.valueOf(300),
                Budget.Period.MONTHLY,
                transportCategory,
                testUser);
        budgetRepository.save(transportBudget);
        transportCategory.getBudgets().add(transportBudget);

        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);

        mockMvc.perform(put("/budgets/"+ budget1.getId() +"/merge")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budgets merged successfully"));

        Budget targetBudget = budgetRepository.findById(budget1.getId()).orElse(null);
        Assertions.assertEquals(0, targetBudget.getAmount().compareTo(BigDecimal.valueOf(1300)));
        Assertions.assertEquals(Budget.Period.MONTHLY, targetBudget.getPeriod());
        Assertions.assertNull(targetBudget.getCategory());

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(1, updatedUser.getBudgets().size());
        Assertions.assertEquals(0, updatedUser.getBudgets().get(0).getAmount().compareTo(BigDecimal.valueOf(1300)));
        Assertions.assertEquals(Budget.Period.MONTHLY, updatedUser.getBudgets().get(0).getPeriod());
        Assertions.assertNull(updatedUser.getBudgets().get(0).getCategory());

        //create subcategory budget
        Budget lunchBudget = new Budget(
                BigDecimal.valueOf(400),
                Budget.Period.MONTHLY,
                lunch,
                testUser);
        budgetRepository.save(lunchBudget);
        lunch.getBudgets().add(lunchBudget);
        ledgerCategoryRepository.save(lunch);

        //merge category budgets
        mockMvc.perform(put("/budgets/"+ foodBudget.getId() +"/merge")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Budgets merged successfully"));

        Budget updatedFoodCategory = budgetRepository.findById(foodBudget.getId()).orElse(null);
        Assertions.assertEquals(0, updatedFoodCategory.getAmount().compareTo(BigDecimal.valueOf(900)));

    }

    //get all budget without category budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllBudget_withoutCategoryBudgets() throws Exception {
        Budget userBudget = new Budget(BigDecimal.valueOf(2000), Budget.Period.MONTHLY, null, testUser);
        budgetRepository.save(userBudget);
        testUser.getBudgets().add(userBudget);

        accountRepository.save(testAccount1);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);


        mockMvc.perform(get("/budgets")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userBudget.amount").value(2000))
                .andExpect(jsonPath("$.userBudget.spent").value(0))
                .andExpect(jsonPath("$.userBudget.remaining").value(2000));
    }

    //get all budget with category budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllBudget_withCategoryBudgets() throws Exception {
        Budget userBudget = new Budget(BigDecimal.valueOf(2000), Budget.Period.MONTHLY, null, testUser);
        budgetRepository.save(userBudget);
        testUser.getBudgets().add(userBudget);

        Budget foodBudget = new Budget(BigDecimal.valueOf(800), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(foodBudget);
        foodCategory.getBudgets().add(foodBudget);

        Budget lunchBudget = new Budget(BigDecimal.valueOf(300), Budget.Period.MONTHLY, lunch, testUser);
        budgetRepository.save(lunchBudget);
        lunch.getBudgets().add(lunchBudget);

        Budget transportBudget = new Budget(BigDecimal.valueOf(150), Budget.Period.MONTHLY, transportCategory, testUser);
        budgetRepository.save(transportBudget);
        transportCategory.getBudgets().add(transportBudget);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);
        ledgerCategoryRepository.save(lunch);

        Transaction tx1=new Expense(LocalDate.now(), BigDecimal.valueOf(170), null, testAccount1,testLedger,foodCategory);
        transactionRepository.save(tx1);
        testLedger.getTransactions().add(tx1);
        testAccount1.addTransaction(tx1);
        foodCategory.getTransactions().add(tx1);

        Transaction tx2=new Expense(LocalDate.now(), BigDecimal.valueOf(100), null, testAccount1,testLedger, transportCategory);
        transactionRepository.save(tx2);
        testLedger.getTransactions().add(tx2);
        testAccount1.addTransaction(tx2);
        transportCategory.getTransactions().add(tx2);

        Transaction tx3=new Expense(LocalDate.now(), BigDecimal.valueOf(50), null, testAccount1,testLedger, lunch);
        transactionRepository.save(tx3);
        testLedger.getTransactions().add(tx3);
        testAccount1.addTransaction(tx3);
        lunch.getTransactions().add(tx3);

        accountRepository.save(testAccount1);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);
        ledgerCategoryRepository.save(lunch);

        mockMvc.perform(get("/budgets")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.userBudget.amount").value(2000))
                .andExpect(jsonPath("$.userBudget.spent").value(320))
                .andExpect(jsonPath("$.userBudget.remaining").value(1680))
                .andExpect(jsonPath("$.categoryBudgets", hasSize(2)))
                .andExpect(jsonPath("$.categoryBudgets[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.categoryBudgets[1].categoryName").value("Transport"))
                .andExpect(jsonPath("$.categoryBudgets[0].amount").value(800))
                .andExpect(jsonPath("$.categoryBudgets[0].spent").value(220))
                .andExpect(jsonPath("$.categoryBudgets[0].remaining").value(580))
                .andExpect(jsonPath("$.categoryBudgets[1].amount").value(150))
                .andExpect(jsonPath("$.categoryBudgets[1].spent").value(100))
                .andExpect(jsonPath("$.categoryBudgets[1].remaining").value(50));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllBudget_withCategoryBudgets_noSubCategoryBudgets() throws Exception {
        Budget userBudget = new Budget(BigDecimal.valueOf(2000), Budget.Period.MONTHLY, null, testUser);
        budgetRepository.save(userBudget);
        testUser.getBudgets().add(userBudget);

        Budget foodBudget = new Budget(BigDecimal.valueOf(800), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(foodBudget);
        foodCategory.getBudgets().add(foodBudget);

        Budget transportBudget = new Budget(BigDecimal.valueOf(150), Budget.Period.MONTHLY, transportCategory, testUser);
        budgetRepository.save(transportBudget);
        transportCategory.getBudgets().add(transportBudget);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);

        Transaction tx1=new Expense(LocalDate.now(), BigDecimal.valueOf(170), null, testAccount1,testLedger,foodCategory);
        transactionRepository.save(tx1);
        testLedger.getTransactions().add(tx1);
        testAccount1.addTransaction(tx1);
        foodCategory.getTransactions().add(tx1);

        Transaction tx2=new Expense(LocalDate.now(), BigDecimal.valueOf(100), null, testAccount1,testLedger, transportCategory);
        transactionRepository.save(tx2);
        testLedger.getTransactions().add(tx2);
        testAccount1.addTransaction(tx2);
        transportCategory.getTransactions().add(tx2);

        Transaction tx3=new Expense(LocalDate.now(), BigDecimal.valueOf(100), null, testAccount1,testLedger, lunch);
        transactionRepository.save(tx3);
        testLedger.getTransactions().add(tx3);
        testAccount1.addTransaction(tx3);
        lunch.getTransactions().add(tx3);

        accountRepository.save(testAccount1);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(transportCategory);

        mockMvc.perform(get("/budgets")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.userBudget.amount").value(2000))
                .andExpect(jsonPath("$.userBudget.spent").value(270))
                .andExpect(jsonPath("$.userBudget.remaining").value(1730))
                .andExpect(jsonPath("$.categoryBudgets", hasSize(2)))
                .andExpect(jsonPath("$.categoryBudgets[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.categoryBudgets[1].categoryName").value("Transport"))
                .andExpect(jsonPath("$.categoryBudgets[0].amount").value(800))
                .andExpect(jsonPath("$.categoryBudgets[0].spent").value(170))
                .andExpect(jsonPath("$.categoryBudgets[0].remaining").value(630))
                .andExpect(jsonPath("$.categoryBudgets[1].amount").value(150))
                .andExpect(jsonPath("$.categoryBudgets[1].spent").value(100))
                .andExpect(jsonPath("$.categoryBudgets[1].remaining").value(50));
    }

    //get category budget with subcategory budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetCategoryBudget_withSubcategoryBudgets() throws Exception {
        Budget foodBudget = new Budget(BigDecimal.valueOf(800), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(foodBudget);
        foodCategory.getBudgets().add(foodBudget);

        Budget lunchBudget = new Budget(BigDecimal.valueOf(300), Budget.Period.MONTHLY, lunch, testUser);
        budgetRepository.save(lunchBudget);
        lunch.getBudgets().add(lunchBudget);


        Transaction tx1=new Expense(LocalDate.now(), BigDecimal.valueOf(170), null, testAccount1,testLedger,foodCategory);
        transactionRepository.save(tx1);
        testLedger.getTransactions().add(tx1);
        testAccount1.addTransaction(tx1);
        foodCategory.getTransactions().add(tx1);

        Transaction tx3=new Expense(LocalDate.now(), BigDecimal.valueOf(50), null, testAccount1,testLedger, lunch);
        transactionRepository.save(tx3);
        testLedger.getTransactions().add(tx3);
        testAccount1.addTransaction(tx3);
        lunch.getTransactions().add(tx3);

        accountRepository.save(testAccount1);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);
        ledgerCategoryRepository.save(lunch);



        mockMvc.perform(get("/budgets/{id}", foodBudget.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.amount").value(800))
                .andExpect(jsonPath("$.spent").value(220))
                .andExpect(jsonPath("$.remaining").value(580))
                .andExpect(jsonPath("$.subCategoryBudgets", hasSize(1)))
                .andExpect(jsonPath("$.subCategoryBudgets[0].subCategory").value("Lunch"))
                .andExpect(jsonPath("$.subCategoryBudgets[0].amount").value(300))
                .andExpect(jsonPath("$.subCategoryBudgets[0].spent").value(50))
                .andExpect(jsonPath("$.subCategoryBudgets[0].remaining").value(250));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetCategoryBudget_withoutSubcategoryBudgets() throws Exception {
        Budget foodBudget = new Budget(BigDecimal.valueOf(800), Budget.Period.MONTHLY, foodCategory, testUser);
        budgetRepository.save(foodBudget);
        foodCategory.getBudgets().add(foodBudget);

        Transaction tx1=new Expense(LocalDate.now(), BigDecimal.valueOf(170), null, testAccount1,testLedger,foodCategory);
        transactionRepository.save(tx1);
        testLedger.getTransactions().add(tx1);
        testAccount1.addTransaction(tx1);
        foodCategory.getTransactions().add(tx1);

        accountRepository.save(testAccount1);
        ledgerRepository.save(testLedger);
        ledgerCategoryRepository.save(foodCategory);

        mockMvc.perform(get("/budgets/{id}", foodBudget.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.amount").value(800))
                .andExpect(jsonPath("$.spent").value(170))
                .andExpect(jsonPath("$.remaining").value(630))
                .andExpect(jsonPath("$.subCategoryBudgets", hasSize(1)))
                .andExpect(jsonPath("$.subCategoryBudgets[0].subCategory").value("Lunch"))
                .andExpect(jsonPath("$.subCategoryBudgets[0].amount").value(0))
                .andExpect(jsonPath("$.subCategoryBudgets[0].spent").value(0))
                .andExpect(jsonPath("$.subCategoryBudgets[0].remaining").value(0));

    }

}
