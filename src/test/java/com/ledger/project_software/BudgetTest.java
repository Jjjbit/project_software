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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class BudgetTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    private User testUser;
    private Ledger testLedger;
    private LedgerCategory foodCategory;
    private LedgerCategory lunch;
    private LedgerCategory transportCategory;

    @BeforeEach
    public void setup() {
        testUser = new User("Alice", "password123");
        userRepository.save(testUser);

        testLedger = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger);
        testUser.getLedgers().add(testLedger);

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

}
