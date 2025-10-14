package com.ledger.project_software;

import com.ledger.project_software.Repository.BudgetRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.business.BudgetController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;


import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class BudgetControllerTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetController budgetController;

    private MockMvc mockMvc;
    private User testUser;
    private Ledger testLedger;
    private LedgerCategory foodCategory;
    private LedgerCategory lunchCategory;
    private LedgerCategory transportCategory;
    private Budget userBudget;
    private Budget foodBudget;
    private Budget lunchBudget;
    private Budget transportBudget;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(budgetController).build();

        testUser = new User("Alice", "pass123");
        testUser.setId(1L);

        testLedger = new Ledger("Alice's Ledger", testUser);
        testUser.getLedgers().add(testLedger);
        testLedger.setId(1L);

        foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        foodCategory.setId(1L);
        testLedger.getCategories().add(foodCategory);

        lunchCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        lunchCategory.setId(2L);
        lunchCategory.setParent(foodCategory);
        foodCategory.getChildren().add(lunchCategory);
        testLedger.getCategories().add(lunchCategory);

        transportCategory = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        transportCategory.setId(3L);
        testLedger.getCategories().add(transportCategory);

        userBudget = new Budget(BigDecimal.valueOf(2000), Budget.Period.MONTHLY, null, testUser);
        userBudget.setId(1L);
        foodBudget = new Budget(BigDecimal.valueOf(800), Budget.Period.MONTHLY, foodCategory, testUser);
        foodBudget.setId(2L);
        lunchBudget = new Budget(BigDecimal.valueOf(300), Budget.Period.MONTHLY, lunchCategory, testUser);
        lunchBudget.setId(3L);
        transportBudget = new Budget(BigDecimal.valueOf(150), Budget.Period.MONTHLY, transportCategory, testUser);
        transportBudget.setId(4L);

    }

    //get all budget without category budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllBudget_withoutCategoryBudgets() throws Exception {

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetRepository.findActiveUncategorizedBudgetByUserId(testUser.getId(), LocalDate.now()))
                .thenReturn(Optional.of(userBudget));

        Mockito.when(transactionRepository.sumExpensesByUserAndPeriod(eq(testUser.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(1200)); // 1200=user spent
        Mockito.when(budgetRepository.findActiveCategoriesBudgetByUserId(testUser.getId(), LocalDate.now()))
                .thenReturn(java.util.Collections.emptyList()); // No category budgets

        mockMvc.perform(get("/budgets")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userBudget.amount").value(2000))
                .andExpect(jsonPath("$.userBudget.spent").value(1200))
                .andExpect(jsonPath("$.userBudget.remaining").value(800));
    }

    //get all budget with category budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetAllBudget_withCategoryBudgets() throws Exception {
        List<Budget> categoryBudgets = List.of(foodBudget, transportBudget);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetRepository.findActiveUncategorizedBudgetByUserId(eq(testUser.getId()), any()))
                .thenReturn(Optional.of(userBudget));

        Mockito.when(transactionRepository.sumExpensesByUserAndPeriod(eq(testUser.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(1200));
        Mockito.when(budgetRepository.findActiveCategoriesBudgetByUserId(eq(testUser.getId()), any()))
                .thenReturn(categoryBudgets);
        Mockito.when(transactionRepository.sumExpensesByCategoryIdsAndPeriod(eq(testUser.getId()),
                        argThat(ids -> ids.contains(foodCategory.getId()) || ids.contains(lunchCategory.getId())),
                        any(),
                        any()))
                .thenReturn(BigDecimal.valueOf(100));
        Mockito.when(transactionRepository.sumExpensesByCategoryIdsAndPeriod(eq(testUser.getId()),
                        argThat(ids -> ids.contains(transportCategory.getId())),
                        any(),
                        any()))
                .thenReturn(BigDecimal.valueOf(10));

        mockMvc.perform(get("/budgets")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.userBudget.amount").value(2000))
                .andExpect(jsonPath("$.userBudget.spent").value(1200))
                .andExpect(jsonPath("$.userBudget.remaining").value(800))
                .andExpect(jsonPath("$.categoryBudgets", hasSize(2)))
                .andExpect(jsonPath("$.categoryBudgets[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.categoryBudgets[1].categoryName").value("Transport"))
                .andExpect(jsonPath("$.categoryBudgets[0].amount").value(800))
                .andExpect(jsonPath("$.categoryBudgets[0].spent").value(100))
                .andExpect(jsonPath("$.categoryBudgets[0].remaining").value(700))
                .andExpect(jsonPath("$.categoryBudgets[1].amount").value(150))
                .andExpect(jsonPath("$.categoryBudgets[1].spent").value(10))
                .andExpect(jsonPath("$.categoryBudgets[1].remaining").value(140));
    }

    //get category budget with subcategory budgets
    @Test
    @WithMockUser(username = "Alice")
    public void testGetCategoryBudget_withSubcategoryBudgets() throws Exception {

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetRepository.findById(eq(foodBudget.getId())))
                .thenReturn(Optional.of(foodBudget));
        Mockito.when(budgetRepository.findActiveCategoryBudget(eq(testUser.getId()),
                        argThat(id -> id.equals(foodCategory.getId())),
                        any(LocalDate.class),
                        eq(foodBudget.getPeriod())))
                .thenReturn(Optional.of(foodBudget));
        Mockito.when(transactionRepository.sumExpensesBySubCategoryAndPeriod(eq(testUser.getId()),
                        argThat(ids -> ids.equals(lunchCategory.getId())),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(50)); // Spent in Lunch subcategory
        Mockito.when(transactionRepository.sumExpensesByCategoryIdsAndPeriod(eq(testUser.getId()),
                        argThat(ids -> ids.size() == 2 &&
                                ids.contains(foodCategory.getId()) &&
                                ids.contains(lunchCategory.getId())),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200)); // Spent in Food category and its subcategories
        Mockito.when(budgetRepository.findActiveSubCategoryBudget(eq(testUser.getId()),
                        argThat(id -> id.equals(lunchCategory.getId())),
                        any(LocalDate.class),
                        eq(foodBudget.getPeriod())))
                .thenReturn(Optional.of(lunchBudget));


        mockMvc.perform(get("/budgets/{id}", foodBudget.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.amount").value(800))
                .andExpect(jsonPath("$.spent").value(200))
                .andExpect(jsonPath("$.remaining").value(600))
                .andExpect(jsonPath("$.subCategoryBudgets", hasSize(1)))
                .andExpect(jsonPath("$.subCategoryBudgets[0].subCategory").value("Lunch"))
                .andExpect(jsonPath("$.subCategoryBudgets[0].amount").value(300))
                .andExpect(jsonPath("$.subCategoryBudgets[0].spent").value(50))
                .andExpect(jsonPath("$.subCategoryBudgets[0].remaining").value(250));
    }

}

