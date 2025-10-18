package com.ledger.project_software;

import com.ledger.project_software.orm.BudgetDAO;
import com.ledger.project_software.orm.LedgerCategoryDAO;
import com.ledger.project_software.orm.TransactionDAO;
import com.ledger.project_software.orm.UserDAO;
import com.ledger.project_software.business.BudgetController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class BudgetStructuralTest {
    @Mock
    private UserDAO userDAO;

    @Mock
    private BudgetDAO budgetDAO;

    @Mock
    private LedgerCategoryDAO ledgerCategoryDAO;

    @Mock
    private TransactionDAO transactionDAO;

    @InjectMocks
    private BudgetController budgetController;

    private User testUser;
    private Ledger testLedger;
    private LedgerCategory parentCategory;
    private LedgerCategory subCategory;
    private Budget categoryBudget;
    private Budget userBudget;
    private Principal principal;

    @BeforeEach
    public void setup() {
        testUser = new User("Alice", "password123");
        testUser.setId(1L);

        testLedger = new Ledger("Test Ledger", testUser);
        testLedger.setId(1L);

        parentCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        parentCategory.setId(10L);

        subCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        subCategory.setId(11L);
        subCategory.setParent(parentCategory);

        categoryBudget = new Budget(BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                parentCategory,
                testUser);
        categoryBudget.setId(100L);

        userBudget = new Budget(BigDecimal.valueOf(2000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        userBudget.setId(200L);

        principal = () -> "Alice";
    }

    //create budget tests
    @Test
    public void testCreateBudget_Success_CategoryBudget() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budget created successfully", response.getBody());
        verify(budgetDAO, times(1)).save(any(Budget.class));
    }

    @Test
    public void testCreateBudget_Success_UncategorizedBudget() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(2000), null, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budget created successfully", response.getBody());
        verify(budgetDAO, times(1)).save(any(Budget.class));
    }

    @Test
    public void testCreateBudget_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 10L, null, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testCreateBudget_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testCreateBudget_BadRequest_NegativeAmount() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(-100), 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Amount must be non-negative", response.getBody());
    }

    @Test
    public void testCreateBudget_NotFound_CategoryNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                budgetController.createBudget(BigDecimal.valueOf(500), 999L, principal, Budget.Period.MONTHLY)
        );
    }

    @Test
    public void testCreateBudget_Forbidden_CategoryNotBelongToUser() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Category does not belong to the user", response.getBody());
    }

    @Test
    public void testCreateBudget_Conflict_BudgetAlreadyExists() {
        Budget existingBudget = new Budget(BigDecimal.valueOf(300),
                Budget.Period.MONTHLY,
                parentCategory,
                testUser);
        parentCategory.getBudgets().add(existingBudget);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Budget for this category and period already exists", response.getBody());
    }

    @Test
    public void testCreateBudget_BadRequest_IncomeCategoryBudget() {
        LedgerCategory incomeCategory = new LedgerCategory("Salary", CategoryType.INCOME, testLedger);
        incomeCategory.setId(20L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(20L)).thenReturn(Optional.of(incomeCategory));

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(500), 20L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cannot set budget for income category", response.getBody());
    }

    @Test
    public void testCreateBudget_Conflict_UncategorizedBudgetAlreadyExists() {
        Budget existingUserBudget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        testUser.getBudgets().add(existingUserBudget);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(2000), null, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Budget for this period already exists", response.getBody());
    }

    @Test
    public void testCreateBudget_Success_ZeroAmount() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.ZERO, 10L, principal, Budget.Period.MONTHLY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void testCreateBudget_Success_YearlyPeriod() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryDAO.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.createBudget(
                BigDecimal.valueOf(6000), 10L, principal, Budget.Period.YEARLY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    //edit budget tests
    @Test
    public void testEditBudget_Success() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.valueOf(800), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budget updated successfully", response.getBody());
        assertEquals(0, BigDecimal.valueOf(800).compareTo(categoryBudget.getAmount()));
        verify(budgetDAO, times(1)).save(categoryBudget);
    }

    @Test
    public void testEditBudget_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.valueOf(800), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testEditBudget_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.valueOf(800), principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testEditBudget_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                budgetController.editBudget(999L, BigDecimal.valueOf(800), principal)
        );
    }

    @Test
    public void testEditBudget_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        categoryBudget.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));

        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.valueOf(800), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Budget does not belong to the user", response.getBody());
    }

    @Test
    public void testEditBudget_BadRequest_NegativeAmount() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));

        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.valueOf(-100), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Amount must be non-negative", response.getBody());
    }

    @Test
    public void testEditBudget_Success_ZeroAmount() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.editBudget(
                100L, BigDecimal.ZERO, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(categoryBudget.getAmount()));
    }

    //merge budget tests
    @Test
    public void testMergeBudgets_Success_UncategorizedBudget() {
        Budget subBudget1 = new Budget(BigDecimal.valueOf(200),
                Budget.Period.MONTHLY,
                parentCategory,
                testUser);
        Budget subBudget2 = new Budget(BigDecimal.valueOf(150),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);

        parentCategory.getBudgets().add(subBudget1);
        subCategory.getBudgets().add(subBudget2);

        testLedger.getCategories().add(parentCategory);
        testLedger.getCategories().add(subCategory);
        testUser.getLedgers().add(testLedger);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(200L)).thenReturn(Optional.of(userBudget));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.mergeBudgets(200L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budgets merged successfully", response.getBody());
        verify(budgetDAO, times(1)).save(userBudget);
    }

    @Test
    public void testMergeBudgets_Success_CategoryBudget() {
        Budget subBudget1 = new Budget(BigDecimal.valueOf(100),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);
        subCategory.getBudgets().add(subBudget1);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.mergeBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budgets merged successfully", response.getBody());
    }

    @Test
    public void testMergeBudgets_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = budgetController.mergeBudgets(200L, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testMergeBudgets_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = budgetController.mergeBudgets(200L, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testMergeBudgets_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                budgetController.mergeBudgets(999L, principal)
        );
    }

    @Test
    public void testMergeBudgets_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        userBudget.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(200L)).thenReturn(Optional.of(userBudget));

        ResponseEntity<String> response = budgetController.mergeBudgets(200L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Target budget does not belong to the user", response.getBody());
    }

    @Test
    public void testMergeBudgets_BadRequest_NotActive() {
        Budget inactiveBudget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        inactiveBudget.setId(300L);
        inactiveBudget.setStartDate(LocalDate.of(2025, 1, 1));
        inactiveBudget.setEndDate(LocalDate.of(2025, 1, 31));

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(300L)).thenReturn(Optional.of(inactiveBudget));

        ResponseEntity<String> response = budgetController.mergeBudgets(300L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cannot merge into a non-active budget", response.getBody());
    }

    @Test
    public void testMergeBudgets_BadRequest_TargetIsSubCategory() {
        Budget subCategoryBudget = new Budget(BigDecimal.valueOf(200),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);
        subCategoryBudget.setId(150L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(150L)).thenReturn(Optional.of(subCategoryBudget));

        ResponseEntity<String> response = budgetController.mergeBudgets(150L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Target budget must be for a category", response.getBody());
    }

    @Test
    public void testMergeBudgets_Success_NoSubBudgets() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.save(any(Budget.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = budgetController.mergeBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(categoryBudget.getAmount()));
    }

    //deleteBudget Tests
    @Test
    public void testDeleteBudget_Success_CategoryBudget() {
        parentCategory.getBudgets().add(categoryBudget);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));

        ResponseEntity<String> response = budgetController.deleteBudget(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budget deleted successfully", response.getBody());
        verify(budgetDAO, times(1)).delete(categoryBudget);
    }

    @Test
    public void testDeleteBudget_Success_UncategorizedBudget() {
        testUser.getBudgets().add(userBudget);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(200L)).thenReturn(Optional.of(userBudget));

        ResponseEntity<String> response = budgetController.deleteBudget(200L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Budget deleted successfully", response.getBody());
        verify(budgetDAO, times(1)).delete(userBudget);
    }

    @Test
    public void testDeleteBudget_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = budgetController.deleteBudget(100L, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testDeleteBudget_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = budgetController.deleteBudget(100L, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthenticated access", response.getBody());
    }

    @Test
    public void testDeleteBudget_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                budgetController.deleteBudget(999L, principal)
        );
    }

    @Test
    public void testDeleteBudget_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        categoryBudget.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));

        ResponseEntity<String> response = budgetController.deleteBudget(100L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Budget does not belong to the user", response.getBody());
    }

    @Test
    public void testDeleteBudget_Success_InactiveBudget() {
        Budget inactiveBudget = new Budget(BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                parentCategory,
                testUser);
        inactiveBudget.setId(300L);
        inactiveBudget.setStartDate(LocalDate.of(2025, 1,1));
        inactiveBudget.setEndDate(LocalDate.of(2025, 1, 31));

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(300L)).thenReturn(Optional.of(inactiveBudget));

        ResponseEntity<String> response = budgetController.deleteBudget(300L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(budgetDAO, times(1)).delete(inactiveBudget);
    }

    //Budget Static Methods Tests
    @Test
    public void testGetStartDateForPeriod_Monthly() {
        LocalDate today = LocalDate.of(2025, 10, 15);

        LocalDate startDate = Budget.getStartDateForPeriod(today, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 10, 1), startDate);
    }

    @Test
    public void testGetStartDateForPeriod_Yearly() {
        LocalDate today = LocalDate.of(2025, 10, 15);

        LocalDate startDate = Budget.getStartDateForPeriod(today, Budget.Period.YEARLY);

        assertEquals(LocalDate.of(2025, 1, 1), startDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);

        LocalDate endDate = Budget.getEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 10, 31), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly_February() {
        LocalDate startDate = LocalDate.of(2025, 2, 1);

        LocalDate endDate = Budget.getEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 2, 28), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly_LeapYear() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);

        LocalDate endDate = Budget.getEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2024, 2, 29), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Yearly() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);

        LocalDate endDate = Budget.getEndDateForPeriod(startDate, Budget.Period.YEARLY);

        assertEquals(LocalDate.of(2025, 12, 31), endDate);
    }

    @Test
    public void testIsActive_Monthly_Active() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 10, 1));
        budget.setEndDate(LocalDate.of(2025, 10, 31));
        LocalDate dateToCheck = LocalDate.of(2025, 10, 15);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }

    @Test
    public void testIsActive_Monthly_Inactive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 11, 1);

        boolean isActive = budget.isActive(dateToCheck);

        assertFalse(isActive);
    }

    @Test
    public void testIsActive_Monthly_LastDayActive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 10, 1));
        budget.setEndDate(LocalDate.of(2025, 10, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 10, 31);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }

    @Test
    public void testIsActive_Yearly_Active() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.YEARLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 6, 15);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }


    @Test
    public void testIsActive_Yearly_Inactive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2024, 1, 1));
        budget.setEndDate(LocalDate.of(2024, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 1, 1);

        boolean isActive = budget.isActive(dateToCheck);

        assertFalse(isActive);
    }

    @Test
    public void testIsActive_Yearly_LastDayActive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.YEARLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 12, 31);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }

    //getAllBudgets Tests
    @Test
    public void testGetAllBudgets_Success_WithUserBudget() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findActiveUncategorizedBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(Optional.of(userBudget));
        Mockito.when(budgetDAO.findActiveCategoriesBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(List.of(categoryBudget));
        Mockito.when(ledgerCategoryDAO.findByParentId(10L)).thenReturn(List.of());
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L),
                        anyList(),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200));

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("userBudget"));
        assertTrue(response.getBody().containsKey("categoryBudgets"));

        @SuppressWarnings("unchecked")
        Map<String, Object> userBudgetMap = (Map<String, Object>) response.getBody().get("userBudget");
        assertEquals(0, BigDecimal.valueOf(2000).compareTo((BigDecimal) userBudgetMap.get("amount")));
    }

    @Test
    public void testGetAllBudgets_Success_WithoutUserBudget() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findActiveUncategorizedBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(Optional.empty());
        Mockito.when(budgetDAO.findActiveCategoriesBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> userBudgetMap = (Map<String, Object>) response.getBody().get("userBudget");
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) userBudgetMap.get("amount")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) userBudgetMap.get("spent")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) userBudgetMap.get("remaining")));
    }

    @Test
    public void testGetAllBudgets_Unauthorized_NullPrincipal() {
        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetAllBudgets_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetAllBudgets_Success_WithSubCategoryBudgets() {
        Budget subBudget = new Budget(BigDecimal.valueOf(150),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);
        subBudget.setId(150L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findActiveUncategorizedBudgetByUserId(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(userBudget));
        Mockito.when(budgetDAO.findActiveCategoriesBudgetByUserId(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(categoryBudget));
        Mockito.when(ledgerCategoryDAO.findByParentId(10L)).thenReturn(List.of(subCategory));
        Mockito.when(budgetDAO.findActiveSubCategoryBudget(
                        eq(1L), eq(11L), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(subBudget));
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200));
        Mockito.when(transactionDAO.sumExpensesBySubCategoryAndPeriod(
                        eq(1L), eq(11L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(50));

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void testGetAllBudgets_Success_NullSpent() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findActiveUncategorizedBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(Optional.of(userBudget));
        Mockito.when(budgetDAO.findActiveCategoriesBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(List.of(categoryBudget));
        Mockito.when(ledgerCategoryDAO.findByParentId(10L)).thenReturn(List.of());
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null);

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoryBudgets = (List<Map<String, Object>>)
                response.getBody().get("categoryBudgets");
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) categoryBudgets.get(0).get("spent")));
    }

    @Test
    public void testGetAllBudgets_Success_MultipleCategoriesSameName() {
        Ledger anotherLedger = new Ledger("Another Ledger", testUser);
        anotherLedger.setId(2L);

        LedgerCategory anotherFoodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, anotherLedger);
        anotherFoodCategory.setId(20L);

        Budget anotherBudget = new Budget(BigDecimal.valueOf(300),
                Budget.Period.MONTHLY,
                anotherFoodCategory,
                testUser);
        anotherBudget.setId(150L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findActiveUncategorizedBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(Optional.of(userBudget));
        Mockito.when(budgetDAO.findActiveCategoriesBudgetByUserId(eq(1L),
                        any(LocalDate.class)))
                .thenReturn(List.of(categoryBudget, anotherBudget));
        Mockito.when(ledgerCategoryDAO.findByParentId(anyLong())).thenReturn(List.of());
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(400));

        ResponseEntity<Map<String, Object>> response = budgetController.getAllBudgets(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoryBudgets = (List<Map<String, Object>>)
                response.getBody().get("categoryBudgets");
        assertEquals(1, categoryBudgets.size()); // Merged by name
        assertEquals("Food", categoryBudgets.get(0).get("categoryName"));
        assertEquals(0, BigDecimal.valueOf(800).compareTo((BigDecimal) categoryBudgets.get(0).get("amount")));
    }

    //getCategoryBudgetsWithSubCategoryBudgets Tests
    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Success() {
        testUser.getLedgers().add(testLedger);
        testLedger.getCategories().add(parentCategory);
        testLedger.getCategories().add(subCategory);
        parentCategory.getChildren().add(subCategory);

        Budget subBudget = new Budget(BigDecimal.valueOf(150),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);
        subBudget.setId(150L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveCategoryBudget(
                        eq(1L), eq(10L), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveSubCategoryBudget(
                        eq(1L), eq(11L), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(subBudget));
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200));
        Mockito.when(transactionDAO.sumExpensesBySubCategoryAndPeriod(
                        eq(1L), eq(11L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(50));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Food", response.getBody().get("category"));
        assertEquals(0, BigDecimal.valueOf(500).compareTo((BigDecimal) response.getBody().get("amount")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subCategoryBudgets = (List<Map<String, Object>>)
                response.getBody().get("subCategoryBudgets");
        assertEquals(1, subCategoryBudgets.size());
        assertEquals("Lunch", subCategoryBudgets.get(0).get("subCategory"));
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Unauthorized_NullPrincipal() {
        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                budgetController.getCategoryBudgetsWithSubCategoryBudgets(999L, principal)
        );
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        categoryBudget.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_BadRequest_UncategorizedBudget() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(200L)).thenReturn(Optional.of(userBudget));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(200L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_BadRequest_SubCategoryBudget() {
        Budget subCategoryBudget = new Budget(BigDecimal.valueOf(150),
                Budget.Period.MONTHLY,
                subCategory,
                testUser);
        subCategoryBudget.setId(150L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(150L)).thenReturn(Optional.of(subCategoryBudget));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(150L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Success_NoSubCategories() {
        testUser.getLedgers().add(testLedger);
        testLedger.getCategories().add(parentCategory);
        parentCategory.getChildren().add(subCategory);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveCategoryBudget(
                        eq(1L),
                        eq(10L),
                        any(LocalDate.class),
                        eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(categoryBudget));
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L),
                        anyList(),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subCategoryBudgets = (List<Map<String, Object>>)
                response.getBody().get("subCategoryBudgets");
        assertEquals(1, subCategoryBudgets.size());
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) subCategoryBudgets.get(0).get("amount")));
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Success_NullSpent() {
        testUser.getLedgers().add(testLedger);
        testLedger.getCategories().add(parentCategory);
        testLedger.getCategories().add(subCategory);
        parentCategory.getChildren().add(subCategory);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveCategoryBudget(
                        eq(1L), eq(10L), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveSubCategoryBudget(
                        eq(1L), eq(11L), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.empty());
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null);
        Mockito.when(transactionDAO.sumExpensesBySubCategoryAndPeriod(
                        eq(1L), eq(11L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null);

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.getBody().get("spent")));
    }

    @Test
    public void testGetCategoryBudgetsWithSubCategoryBudgets_Success_MultipleLedgersSameCategoryName() {
        Ledger anotherLedger = new Ledger("Another Ledger", testUser);
        anotherLedger.setId(2L);

        LedgerCategory anotherFoodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, anotherLedger);
        anotherFoodCategory.setId(20L);

        Budget anotherBudget = new Budget(BigDecimal.valueOf(300),
                Budget.Period.MONTHLY,
                anotherFoodCategory,
                testUser);
        anotherBudget.setId(150L);

        testUser.getLedgers().add(testLedger);
        testUser.getLedgers().add(anotherLedger);
        testLedger.getCategories().add(parentCategory);
        anotherLedger.getCategories().add(anotherFoodCategory);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(budgetDAO.findById(100L)).thenReturn(Optional.of(categoryBudget));
        Mockito.when(budgetDAO.findActiveCategoryBudget(
                        anyLong(), anyLong(), any(LocalDate.class), eq(Budget.Period.MONTHLY)))
                .thenReturn(Optional.of(categoryBudget), Optional.of(anotherBudget));
        Mockito.when(transactionDAO.sumExpensesByCategoryIdsAndPeriod(
                        eq(1L), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(200), BigDecimal.valueOf(150));

        ResponseEntity<Map<String, Object>> response = budgetController
                .getCategoryBudgetsWithSubCategoryBudgets(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        assertEquals(0, BigDecimal.valueOf(800).compareTo((BigDecimal) response.getBody().get("amount")));
    }
}
