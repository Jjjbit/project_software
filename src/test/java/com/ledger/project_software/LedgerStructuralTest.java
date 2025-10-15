package com.ledger.project_software;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.business.LedgerController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class) // inizializza i mock e gli injectMocks
public class LedgerStructuralTest {
    @Mock
    private UserRepository userRepository; //mock del repository per simulare il comportamento senza collegarsi al database

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;


    @InjectMocks
    private LedgerController ledgerController; //controller con il mock del repository iniettato

    private User testUser;
    private Ledger testLedger;
    private Principal principal;


    @BeforeEach
    public void setUp() {

        testUser = new User("Alice", "pass123");
        testUser.setId(1L);

        testLedger = new Ledger("Test Ledger", testUser);
        testLedger.setId(1L);

        principal = () -> "Alice"; //simula un utente autenticato con username "Alice"
    }

    //createLedger tests
    @Test
    public void testCreateLedger_Success() {
        Category parentCategory = new Category("Food", CategoryType.EXPENSE);
        parentCategory.setId(1L);

        Category childCategory = new Category("Lunch", CategoryType.EXPENSE);
        childCategory.setId(2L);
        childCategory.setParent(parentCategory);
        parentCategory.getChildren().add(childCategory);

        List<Category> templateCategories = List.of(parentCategory);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findByName("New Ledger")).thenReturn(null);
        Mockito.when(categoryRepository.findByParentIsNull()).thenReturn(templateCategories);
        Mockito.when(ledgerRepository.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("ledger created successfully", response.getBody());
        verify(userRepository, times(1)).findByUsername("Alice");
        verify(ledgerRepository, times(1)).findByName("New Ledger");
        verify(categoryRepository, times(1)).findByParentIsNull();
        verify(ledgerRepository, times(1)).save(any(Ledger.class));
    }

    @Test
    public void testCreateLedger_Unauthorized_UserNotFound() {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("User not found", response.getBody());
        verify(userRepository, times(1)).findByUsername("Alice");
        verify(ledgerRepository, never()).save(any(Ledger.class));
    }

    @Test
    public void testCreateLedger_Conflict_NameExists() {
        Ledger existingLedger = new Ledger("New Ledger", testUser);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findByName("New Ledger")).thenReturn(existingLedger);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Assertions.assertEquals("Ledger name already exists", response.getBody());
        verify(ledgerRepository, times(1)).findByName("New Ledger");
        verify(ledgerRepository, never()).save(any(Ledger.class));
    }

    @Test
    public void testCreateLedger_Success_WithNestedCategories() {
        Category parent = new Category("Parent", CategoryType.EXPENSE);
        parent.setId(1L);

        Category child1 = new Category("Child1", CategoryType.EXPENSE);
        child1.setId(2L);
        child1.setParent(parent);

        Category child2 = new Category("Child2", CategoryType.EXPENSE);
        child2.setId(3L);
        child2.setParent(parent);

        parent.getChildren().add(child1);
        parent.getChildren().add(child2);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findByName("New Ledger")).thenReturn(null);
        Mockito.when(categoryRepository.findByParentIsNull()).thenReturn(List.of(parent));
        Mockito.when(ledgerRepository.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(categoryRepository, times(1)).findByParentIsNull();
        verify(ledgerRepository, times(1)).save(any(Ledger.class));
    }

    //deleteLedger tests
    @Test
    public void testDeleteLedger_Success_NoTransactions() {
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger deleted successfully", response.getBody());
        verify(ledgerRepository, times(1)).delete(testLedger);
    }

    @Test
    public void testDeleteLedger_NotFound() {
        Mockito.when(ledgerRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ledgerController.deleteLedger(999L, principal);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Ledger not found", response.getBody());
        verify(ledgerRepository, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Unauthorized_NullPrincipal() {
        // Arrange
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, null);

        // Assert
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
        verify(ledgerRepository, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Unauthorized_UserNotFound() {
        // Arrange
        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(null);

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        // Assert
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
        verify(ledgerRepository, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Success_WithIncomeTransaction() {
        // Arrange
        Account account = new BasicAccount("Cash", BigDecimal.valueOf(1000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        account.setId(100L);

        LedgerCategory category = new LedgerCategory("Salary", CategoryType.INCOME, testLedger);
        category.setId(10L);

        Income income = new Income(LocalDate.now(), BigDecimal.valueOf(500),
                "Salary", account, testLedger, category);

        testLedger.getTransactions().add(income);

        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accountRepository, times(1)).save(account);
        verify(transactionRepository, times(1)).delete(income);
        verify(ledgerRepository, times(1)).delete(testLedger);
    }

    @Test
    public void testDeleteLedger_Success_WithExpenseTransaction() {
        // Arrange
        Account account = new BasicAccount("Cash", BigDecimal.valueOf(1000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        account.setId(100L);

        LedgerCategory category = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        category.setId(10L);

        Expense expense = new Expense(LocalDate.now(), BigDecimal.valueOf(50),
                "Lunch", account, testLedger, category);

        testLedger.getTransactions().add(expense);

        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accountRepository, times(1)).save(account);
        verify(transactionRepository, times(1)).delete(expense);
    }

    @Test
    public void testDeleteLedger_Success_WithTransferTransaction() {
        // Arrange
        Account fromAccount = new BasicAccount("Cash", BigDecimal.valueOf(1000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        fromAccount.setId(100L);

        Account toAccount = new BasicAccount("Savings", BigDecimal.valueOf(500),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        toAccount.setId(200L);

        Transfer transfer = new Transfer(LocalDate.now(), "Transfer",
                fromAccount, toAccount, BigDecimal.valueOf(100), testLedger);

        testLedger.getTransactions().add(transfer);

        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accountRepository, times(2)).save(any(Account.class)); // both accounts
        verify(transactionRepository, times(1)).delete(transfer);
    }

    @Test
    public void testDeleteLedger_Success_WithCategories() {
        // Arrange
        LedgerCategory category1 = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        category1.setId(1L);

        LedgerCategory category2 = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        category2.setId(2L);

        testLedger.getCategories().add(category1);
        testLedger.getCategories().add(category2);

        Mockito.when(ledgerRepository.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);

        // Act
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ledgerCategoryRepository, times(2)).delete(any(LedgerCategory.class));
        verify(ledgerRepository, times(1)).delete(testLedger);
    }

    //copyLedger tests
}
