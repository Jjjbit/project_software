package com.ledger.project_software;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.business.LedgerCategoryController;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.security.Principal;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LedgerCategoryStructuralTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private LedgerCategoryController ledgerCategoryController;

    private User testUser;
    private Ledger testLedger;
    private LedgerCategory parentCategory;
    private LedgerCategory subCategory;
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

        principal = () -> "Alice";
    }

    //createCategory Tests
    @Test
    public void testCreateCategory_Success() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Transport"))
                .thenReturn(false);
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "Transport", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Category created successfully", response.getBody());
        verify(ledgerCategoryRepository, times(1)).save(any(LedgerCategory.class));
        verify(ledgerRepository, times(1)).save(testLedger);
    }

    @Test
    public void testCreateCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "Transport", null, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
        verify(ledgerCategoryRepository, never()).save(any());
    }

    @Test
    public void testCreateCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "Transport", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testCreateCategory_BadRequest_NullLedgerId() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "Transport", principal, null, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must provide ledgerId", response.getBody());
    }

    @Test
    public void testCreateCategory_NotFound_LedgerNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.createCategory("Transport", principal, 999L, CategoryType.EXPENSE)
        );
    }

    @Test
    public void testCreateCategory_BadRequest_NullName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                null, principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name cannot be empty", response.getBody());
    }

    @Test
    public void testCreateCategory_BadRequest_EmptyName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "   ", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name cannot be empty", response.getBody());
    }

    @Test
    public void testCreateCategory_BadRequest_NameTooLong() {
        String longName = "a".repeat(101);
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                longName, principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name too long", response.getBody());
    }

    @Test
    public void testCreateCategory_Conflict_NameExists() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Food"))
                .thenReturn(true);
        ResponseEntity<String> response = ledgerCategoryController.createCategory(
                "Food", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Category name must be unique within the ledger", response.getBody());
    }

    //createSubCategory Tests
    @Test
    public void testCreateSubCategory_Success() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Dinner"))
                .thenReturn(false);
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "Dinner", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SubCategory created successfully", response.getBody());
        verify(ledgerCategoryRepository, times(2)).save(any(LedgerCategory.class));
    }

    @Test
    public void testCreateSubCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "Dinner", null, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testCreateSubCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "Dinner", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testCreateSubCategory_BadRequest_NullParentId() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                null, "Dinner", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must provide parentId", response.getBody());
    }

    @Test
    public void testCreateSubCategory_NotFound_ParentNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.createSubCategory(999L, "Dinner", principal, 1L, CategoryType.EXPENSE)
        );
    }

    @Test
    public void testCreateSubCategory_BadRequest_ParentIsSubCategory() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                11L, "Dinner", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Parent must be a Category", response.getBody());
    }

    @Test
    public void testCreateSubCategory_BadRequest_NullLedgerId() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "Dinner", principal, null, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must provide ledgerId", response.getBody());
    }

    @Test
    public void testCreateSubCategory_NotFound_LedgerNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.createSubCategory(10L, "Dinner", principal, 999L, CategoryType.EXPENSE)
        );
    }

    @Test
    public void testCreateSubCategory_BadRequest_NullName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, null, principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name cannot be empty", response.getBody());
    }

    @Test
    public void testCreateSubCategory_BadRequest_EmptyName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "   ", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name cannot be empty", response.getBody());
    }

    @Test
    public void testCreateSubCategory_BadRequest_NameTooLong() {
        String longName = "a".repeat(101);
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, longName, principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name too long", response.getBody());
    }

    @Test
    public void testCreateSubCategory_Conflict_NameExists() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Lunch"))
                .thenReturn(true);

        ResponseEntity<String> response = ledgerCategoryController.createSubCategory(
                10L, "Lunch", principal, 1L, CategoryType.EXPENSE);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SubCategory name must be unique within the ledger", response.getBody());
    }

    // demoteCategoryToSubCategory Tests
    @Test
    public void testDemoteCategoryToSubCategory_Success() {
        LedgerCategory categoryToDemote = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDemote.setId(20L);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDemote));
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                20L, principal, 10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Demoted successfully", response.getBody());
        verify(ledgerCategoryRepository, times(2)).save(any(LedgerCategory.class));
    }

    @Test
    public void testDemoteCategoryToSubCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                20L, null, 10L);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                20L, principal, 10L);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_NotFound_CategoryNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.demoteCategoryToSubCategory(999L, principal, 10L)
        );
    }

    @Test
    public void testDemoteCategoryToSubCategory_BadRequest_AlreadySubCategory() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                11L, principal, 10L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must be a Category", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_BadRequest_NullParentId() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                10L, principal, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Demote must have parentId", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_BadRequest_SameIdAsParent() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                10L, principal, 10L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Demote must have parentId", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_NotFound_ParentNotFound() {
        LedgerCategory categoryToDemote = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDemote.setId(20L);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDemote));
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.demoteCategoryToSubCategory(20L, principal, 999L)
        );
    }

    @Test
    public void testDemoteCategoryToSubCategory_BadRequest_ParentIsSubCategory() {
        LedgerCategory categoryToDemote = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDemote.setId(20L);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDemote));
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                20L, principal, 11L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("parent must be Category", response.getBody());
    }

    @Test
    public void testDemoteCategoryToSubCategory_BadRequest_HasSubCategories() {
        parentCategory.getChildren().add(subCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.demoteCategoryToSubCategory(
                10L, principal, 20L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cannot demote category with subcategories", response.getBody());
    }

    //promoteSubCategoryToCategory Tests
    @Test
    public void testPromoteSubCategoryToCategory_Success() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        parentCategory.getChildren().add(subCategory);

        ResponseEntity<String> response = ledgerCategoryController.promoteSubCategoryToCategory(
                11L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Promoted successfully", response.getBody());
        verify(ledgerCategoryRepository, times(2)).save(any(LedgerCategory.class));
    }

    @Test
    public void testPromoteSubCategoryToCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.promoteSubCategoryToCategory(
                11L, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testPromoteSubCategoryToCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.promoteSubCategoryToCategory(
                11L, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testPromoteSubCategoryToCategory_NotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.promoteSubCategoryToCategory(999L, principal)
        );
    }

    @Test
    public void testPromoteSubCategoryToCategory_BadRequest_AlreadyCategory() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.promoteSubCategoryToCategory(
                10L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must be a SubCategory", response.getBody());
    }

    //deleteCategory Tests
    @Test
    public void testDeleteCategory_Success_DeleteTransactions() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        Account account = new BasicAccount("Cash",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        account.setId(100L);

        Expense expense = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Taxi",
                account,
                testLedger,
                categoryToDelete);
        categoryToDelete.getTransactions().add(expense);
        testLedger.getCategories().add(categoryToDelete);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, true, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Deleted successfully", response.getBody());
        verify(transactionRepository, times(1)).delete(expense);
        verify(ledgerCategoryRepository, times(1)).delete(categoryToDelete);
    }

    @Test
    public void testDeleteCategory_Success_MigrateTransactions() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        LedgerCategory migrateToCategory = new LedgerCategory("Other", CategoryType.EXPENSE, testLedger);
        migrateToCategory.setId(30L);

        Transaction transaction = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Taxi",
                null,
                testLedger,
                categoryToDelete);

        categoryToDelete.getTransactions().add(transaction);
        testLedger.getCategories().add(categoryToDelete);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));
        Mockito.when(ledgerCategoryRepository.findById(30L)).thenReturn(Optional.of(migrateToCategory));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, false, 30L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Deleted successfully", response.getBody());
        verify(transactionRepository, times(1)).save(transaction);
        verify(ledgerCategoryRepository, times(1)).delete(categoryToDelete);
        verify(transactionRepository, never()).delete(any());
    }

    @Test
    public void testDeleteCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, null, true, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testDeleteCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, true, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testDeleteCategory_NotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.deleteCategory(999L, principal, true, null)
        );
    }

    @Test
    public void testDeleteCategory_BadRequest_HasSubCategories() {
        parentCategory.getChildren().add(subCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                10L, principal, true, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cannot delete category with subcategories", response.getBody());
    }

    @Test
    public void testDeleteCategory_BadRequest_MigrateWithoutCategoryId() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        Transaction transaction = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Taxi",
                null,
                testLedger,
                categoryToDelete);
        categoryToDelete.getTransactions().add(transaction);
        testLedger.getCategories().add(categoryToDelete);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, false, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must provide migrateToCategoryId", response.getBody());
    }

    @Test
    public void testDeleteCategory_NotFound_MigrateCategoryNotFound() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        Transaction transaction = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Taxi",
                null,
                testLedger,
                categoryToDelete);
        categoryToDelete.getTransactions().add(transaction);
        testLedger.getCategories().add(categoryToDelete);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.deleteCategory(20L, principal, false, 999L)
        );
    }

    @Test
    public void testDeleteCategory_BadRequest_MigrateCategoryIsSubCategory() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        Transaction transaction = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Taxi",
                null,
                testLedger,
                categoryToDelete);
        categoryToDelete.getTransactions().add(transaction);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, false, 11L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("migrateToCategory must be a Category", response.getBody());
    }

    @Test
    public void testDeleteCategory_Success_WithBudgets() {
        LedgerCategory categoryToDelete = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        categoryToDelete.setId(20L);

        Budget budget = new Budget(BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                categoryToDelete,
                testUser);
        budget.setId(1L);
        categoryToDelete.getBudgets().add(budget);

        testLedger.getCategories().add(categoryToDelete);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(categoryToDelete));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                20L, principal, true, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(budgetRepository, times(1)).deleteAll(anyList());
        verify(ledgerCategoryRepository, times(1)).delete(categoryToDelete);
    }

    @Test
    public void testDeleteCategory_Success_SubCategoryWithParent() {
        parentCategory.getChildren().add(subCategory);
        testLedger.getCategories().add(subCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));

        ResponseEntity<String> response = ledgerCategoryController.deleteCategory(
                11L, principal, true, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ledgerCategoryRepository, times(1)).save(parentCategory);
        verify(ledgerCategoryRepository, times(1)).delete(subCategory);
    }

    // renameCategory Tests
    @Test
    public void testRenameCategory_Success() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Groceries"))
                .thenReturn(false);
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "Groceries", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Renamed successfully", response.getBody());
        assertEquals("Groceries", parentCategory.getName());
        verify(ledgerCategoryRepository, times(1)).save(parentCategory);
    }

    @Test
    public void testRenameCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "Groceries", null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testRenameCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "Groceries", principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testRenameCategory_NotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.renameCategory(999L, "Groceries", principal)
        );
    }

    @Test
    public void testRenameCategory_BadRequest_NullName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, null, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("new name cannot be null", response.getBody());
    }

    @Test
    public void testRenameCategory_BadRequest_EmptyName() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "   ", principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("new name cannot be null", response.getBody());
    }

    @Test
    public void testRenameCategory_Conflict_NameExistsForDifferentCategory() {
        LedgerCategory existingCategory = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        existingCategory.setId(20L);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Transport"))
                .thenReturn(true);
        Mockito.when(ledgerCategoryRepository.findByLedgerAndName(testLedger, "Transport"))
                .thenReturn(existingCategory);

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "Transport", principal);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("new name exists already", response.getBody());
    }

    @Test
    public void testRenameCategory_Success_SameNameSameCategory() {
        parentCategory.setName("Food");

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, "Food"))
                .thenReturn(true);
        Mockito.when(ledgerCategoryRepository.findByLedgerAndName(testLedger, "Food"))
                .thenReturn(parentCategory);

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, "Food", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Renamed successfully", response.getBody());
    }

    @Test
    public void testRenameCategory_BadRequest_NameTooLong() {
        String longName = "a".repeat(101);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.existsByLedgerAndName(testLedger, longName))
                .thenReturn(false);

        ResponseEntity<String> response = ledgerCategoryController.renameCategory(10L, longName, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name too long", response.getBody());
    }

    // changeParentCategory Tests
    @Test
    public void testChangeParentCategory_Success() {
        LedgerCategory newParent = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        newParent.setId(20L);

        LedgerCategory oldParent = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        oldParent.setId(10L);
        oldParent.getChildren().add(subCategory);
        subCategory.setParent(oldParent);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(newParent));
        Mockito.when(ledgerCategoryRepository.save(any(LedgerCategory.class)))
                .thenAnswer(i -> i.getArguments()[0]);


        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(11L, 20L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Parent category changed successfully", response.getBody());
        verify(ledgerCategoryRepository, times(3)).save(any(LedgerCategory.class));
    }

    @Test
    public void testChangeParentCategory_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(11L, 20L, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testChangeParentCategory_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(11L, 20L, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testChangeParentCategory_BadRequest_SameIdAsParent() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(10L, 10L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Category cannot be its own parent", response.getBody());
    }

    @Test
    public void testChangeParentCategory_NotFound_CategoryNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.changeParentCategory(999L, 20L, principal)
        );
    }

    @Test
    public void testChangeParentCategory_NotFound_NewParentNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.changeParentCategory(11L, 999L, principal)
        );
    }

    @Test
    public void testChangeParentCategory_BadRequest_NewParentIsSubCategory() {
        LedgerCategory anotherSubCategory = new LedgerCategory("Breakfast", CategoryType.EXPENSE, testLedger);
        anotherSubCategory.setId(12L);
        anotherSubCategory.setParent(parentCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(ledgerCategoryRepository.findById(12L)).thenReturn(Optional.of(anotherSubCategory));

        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(11L, 12L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("New parent must be a Category", response.getBody());
    }

    @Test
    public void testChangeParentCategory_BadRequest_CategoryIsNotSubCategory() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        LedgerCategory newParent = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        newParent.setId(20L);
        Mockito.when(ledgerCategoryRepository.findById(20L)).thenReturn(Optional.of(newParent));

        ResponseEntity<String> response = ledgerCategoryController.changeParentCategory(10L, 20L, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Must be a SubCategory", response.getBody());
    }

    //getCategoryTransactionsForMonth Tests
    @Test
    public void testGetCategoryTransactionsForMonth_Success_ParentCategory_WithMonth() {
        YearMonth month = YearMonth.of(2025, 10);

        Transaction tx1 = new Expense(LocalDate.of(2025, 10, 5),
                BigDecimal.valueOf(50),
                "Lunch",
                null,
                testLedger,
                parentCategory);
        Transaction tx2 = new Expense(LocalDate.of(2025, 10, 10),
                BigDecimal.valueOf(30),
                "Dinner",
                null,
                testLedger,
                subCategory);

        List<Transaction> transactions = List.of(tx1, tx2);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.findByParentId(10L)).thenReturn(List.of(subCategory));
        Mockito.when(transactionRepository.findByCategoryIdsAndUserId(
                        anyList(), any(LocalDate.class), any(LocalDate.class), eq(1L)))
                .thenReturn(transactions);

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(10L, principal, month);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Success_SubCategory_WithMonth() {
        YearMonth month = YearMonth.of(2025, 10);

        Transaction tx1 = new Expense(LocalDate.of(2025, 10, 5),
                BigDecimal.valueOf(50),
                "Lunch",
                null,
                testLedger,
                subCategory);
        List<Transaction> transactions = List.of(tx1);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(transactionRepository.findByCategoryIdAndUserId(
                        eq(11L), any(LocalDate.class), any(LocalDate.class), eq(1L)))
                .thenReturn(transactions);

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(11L, principal, month);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Success_WithoutMonth() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(transactionRepository.findByCategoryIdAndUserId(
                        eq(11L), any(LocalDate.class), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of());

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(11L, principal, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Unauthorized_NullPrincipal() {
        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(10L, null, YearMonth.now());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(10L, principal, YearMonth.now());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_NotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                ledgerCategoryController.getCategoryTransactionsForMonth(999L, principal, YearMonth.now())
        );
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(10L, principal, YearMonth.now());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Success_ParentCategoryWithNoSubCategories() {
        YearMonth month = YearMonth.of(2025, 10);

        Transaction tx1 = new Expense(LocalDate.of(2025, 10, 5), BigDecimal.valueOf(50),
                "Expense", null, testLedger, parentCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(10L)).thenReturn(Optional.of(parentCategory));
        Mockito.when(ledgerCategoryRepository.findByParentId(10L)).thenReturn(List.of());
        Mockito.when(transactionRepository.findByCategoryIdsAndUserId(
                        anyList(), any(LocalDate.class), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(tx1));

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(10L, principal, month);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testGetCategoryTransactionsForMonth_Success_NoTransactions() {
        YearMonth month = YearMonth.of(2025, 10);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(11L)).thenReturn(Optional.of(subCategory));
        Mockito.when(transactionRepository.findByCategoryIdAndUserId(
                        eq(11L), any(LocalDate.class), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of());

        ResponseEntity<List<Transaction>> response = ledgerCategoryController
                .getCategoryTransactionsForMonth(11L, principal, month);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
    }

}
