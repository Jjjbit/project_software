package com.ledger.project_software;

import com.ledger.project_software.orm.*;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class) // inizializza i mock e gli injectMocks
public class LedgerStructuralTest {
    @Mock
    private UserDAO userDAO; //mock del repository per simulare il comportamento senza collegarsi al database

    @Mock
    private LedgerDAO ledgerDAO;

    @Mock
    private LedgerCategoryDAO ledgerCategoryDAO;

    @Mock
    private CategoryDAO categoryDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private TransactionDAO transactionDAO;


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

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findByName("New Ledger")).thenReturn(null);
        Mockito.when(categoryDAO.findByParentIsNull()).thenReturn(templateCategories);
        Mockito.when(ledgerDAO.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("ledger created successfully", response.getBody());
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(ledgerDAO, times(1)).findByName("New Ledger");
        verify(categoryDAO, times(1)).findByParentIsNull();
        verify(ledgerDAO, times(1)).save(any(Ledger.class));
    }

    @Test
    public void testCreateLedger_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("User not found", response.getBody());
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(ledgerDAO, never()).save(any(Ledger.class));
    }

    @Test
    public void testCreateLedger_Conflict_NameExists() {
        Ledger existingLedger = new Ledger("New Ledger", testUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findByName("New Ledger")).thenReturn(existingLedger);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Assertions.assertEquals("Ledger name already exists", response.getBody());
        verify(ledgerDAO, times(1)).findByName("New Ledger");
        verify(ledgerDAO, never()).save(any(Ledger.class));
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

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findByName("New Ledger")).thenReturn(null);
        Mockito.when(categoryDAO.findByParentIsNull()).thenReturn(List.of(parent));
        Mockito.when(ledgerDAO.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.createLedger("New Ledger", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(categoryDAO, times(1)).findByParentIsNull();
        verify(ledgerDAO, times(1)).save(any(Ledger.class));
    }

    //deleteLedger tests
    @Test
    public void testDeleteLedger_Success_NoTransactions() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger deleted successfully", response.getBody());
        verify(ledgerDAO, times(1)).delete(testLedger);
    }

    @Test
    public void testDeleteLedger_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ledgerController.deleteLedger(999L, principal);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Ledger not found", response.getBody());
        verify(ledgerDAO, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerController.deleteLedger(1L, null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
        verify(ledgerDAO, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
        verify(ledgerDAO, never()).delete(any());
    }

    @Test
    public void testDeleteLedger_Success_WithIncomeTransaction() {
        Account account = new BasicAccount("Cash",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        account.setId(100L);

        LedgerCategory category = new LedgerCategory("Salary", CategoryType.INCOME, testLedger);
        category.setId(10L);

        Income income = new Income(LocalDate.now(),
                BigDecimal.valueOf(500),
                "Salary",
                account,
                testLedger,
                category);
        testLedger.getTransactions().add(income);
        account.addTransaction(income);
        category.getTransactions().add(income);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger deleted successfully", response.getBody());
        verify(accountDAO, times(1)).save(account);
        verify(transactionDAO, times(1)).delete(income);
        verify(ledgerCategoryDAO, times(1)).save(category);
        verify(ledgerDAO, times(1)).delete(testLedger);
    }

    @Test
    public void testDeleteLedger_Success_WithExpenseTransaction() {
        Account account = new BasicAccount("Cash",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        account.setId(100L);

        LedgerCategory category = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        category.setId(10L);

        Expense expense = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Lunch",
                account,
                testLedger,
                category);
        testLedger.getTransactions().add(expense);
        account.addTransaction(expense);
        category.getTransactions().add(expense);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger deleted successfully", response.getBody());
        verify(accountDAO, times(1)).save(account);
        verify(ledgerCategoryDAO, times(1)).save(category);
        verify(ledgerDAO, times(1)).delete(testLedger);
        verify(transactionDAO, times(1)).delete(expense);
    }

    @Test
    public void testDeleteLedger_Success_WithTransferTransaction() {
        Account fromAccount = new BasicAccount("Cash",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        fromAccount.setId(100L);

        Account toAccount = new BasicAccount("Savings",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        toAccount.setId(200L);

        Transfer transfer = new Transfer(LocalDate.now(), "Transfer", fromAccount, toAccount, BigDecimal.valueOf(100), testLedger);
        testLedger.getTransactions().add(transfer);
        fromAccount.addTransaction(transfer);
        toAccount.addTransaction(transfer);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accountDAO, times(2)).save(any(Account.class)); // both accounts
        verify(transactionDAO, times(1)).delete(transfer);
        verify(ledgerDAO, times(1)).delete(testLedger);
    }

    @Test
    public void testDeleteLedger_Success_WithCategories() {
        LedgerCategory category1 = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        category1.setId(1L);

        LedgerCategory category2 = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        category2.setId(2L);

        testLedger.getCategories().add(category1);
        testLedger.getCategories().add(category2);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.deleteLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ledgerCategoryDAO, times(2)).delete(any(LedgerCategory.class));
        verify(ledgerDAO, times(1)).delete(testLedger);
    }

    //copyLedger tests
    @Test
    public void testCopyLedger_Success() {
        LedgerCategory parentCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        parentCategory.setId(1L);

        LedgerCategory childCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        childCategory.setId(2L);
        childCategory.setParent(parentCategory);
        parentCategory.getChildren().add(childCategory);

        testLedger.getCategories().add(parentCategory);
        testLedger.getCategories().add(childCategory);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerDAO.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]); // Simula il salvataggio

        ResponseEntity<String> response = ledgerController.copyLedger(1L, principal);


        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("copy ledger", response.getBody());
        verify(ledgerDAO, times(1)).save(any(Ledger.class));
    }

    @Test
    public void testCopyLedger_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerController.copyLedger(1L, null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
        verify(ledgerDAO, never()).save(any());
    }

    @Test
    public void testCopyLedger_BadRequest_NullLedgerId() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerController.copyLedger(null, principal);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Invalid ledger ID", response.getBody());
    }

    @Test
    public void testCopyLedger_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ledgerController.copyLedger(999L, principal);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Ledger not found", response.getBody());
    }

    @Test
    public void testCopyLedger_Unauthorized_OwnerNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerController.copyLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testCopyLedger_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.copyLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Assertions.assertEquals("You do not have permission to copy this ledger", response.getBody());
    }

    @Test
    public void testCopyLedger_Success_WithNestedCategories() {
        LedgerCategory parent = new LedgerCategory("Parent", CategoryType.EXPENSE, testLedger);
        parent.setId(1L);

        LedgerCategory child1 = new LedgerCategory("Child1", CategoryType.EXPENSE, testLedger);
        child1.setId(2L);
        child1.setParent(parent);

        LedgerCategory child2 = new LedgerCategory("Child2", CategoryType.EXPENSE, testLedger);
        child2.setId(3L);
        child2.setParent(parent);

        parent.getChildren().add(child1);
        parent.getChildren().add(child2);

        testLedger.getCategories().add(parent);
        testLedger.getCategories().add(child1);
        testLedger.getCategories().add(child2);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerDAO.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.copyLedger(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ledgerDAO, times(1)).save(any(Ledger.class));
    }

    //renameLedger tests
    @Test
    public void testRenameLedger_Success() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerDAO.findByName("New Name")).thenReturn(null);
        Mockito.when(ledgerDAO.save(any(Ledger.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<String> response = ledgerController.renameLedger(1L, "New Name", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger renamed successfully", response.getBody());
        Assertions.assertEquals("New Name", testLedger.getName());
        verify(ledgerDAO, times(1)).save(testLedger);
    }

    @Test
    public void testRenameLedger_BadRequest_NullLedgerId() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = ledgerController.renameLedger(null, "New Name", principal);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Invalid ledger ID", response.getBody());
    }

    @Test
    public void testRenameLedger_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = ledgerController.renameLedger(1L, "New Name", null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testRenameLedger_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = ledgerController.renameLedger(999L, "New Name", principal);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("Ledger not found", response.getBody());
    }

    @Test
    public void testRenameLedger_Unauthorized_OwnerNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<String> response = ledgerController.renameLedger(1L, "New Name", principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Unauthorized access", response.getBody());
    }

    @Test
    public void testRenameLedger_Conflict_NameExistsForDifferentLedger() {
        Ledger anotherLedger = new Ledger("Existing Name", testUser);
        anotherLedger.setId(2L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerDAO.findByName("Existing Name")).thenReturn(anotherLedger);

        ResponseEntity<String> response = ledgerController.renameLedger(1L, "Existing Name", principal);

        Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Assertions.assertEquals("Ledger name already exists", response.getBody());
    }

    @Test
    public void testRenameLedger_Success_SameNameSameLedger() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerDAO.findByName("Test Ledger")).thenReturn(testLedger);

        ResponseEntity<String> response = ledgerController.renameLedger(1L, "Test Ledger", principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Ledger renamed successfully", response.getBody());
    }

    @Test
    public void testRenameLedger_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.renameLedger(1L, "New Name", principal);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Assertions.assertEquals("You do not have permission to rename this ledger", response.getBody());
    }

    @Test
    public void testRenameLedger_BadRequest_NullName() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.renameLedger(1L, null, principal);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("ledger name cannot be empty", response.getBody());
    }

    @Test
    public void testRenameLedger_BadRequest_EmptyName() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<String> response = ledgerController.renameLedger(1L, " ", principal);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("ledger name cannot be empty", response.getBody());
    }

    //getAllLedgers tests
    @Test
    public void testGetAllLedgers_Success() {
        Ledger ledger1 = new Ledger("Ledger 1", testUser);
        Ledger ledger2 = new Ledger("Ledger 2", testUser);
        List<Ledger> ledgers = List.of(ledger1, ledger2);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findByOwner(testUser)).thenReturn(ledgers);

        ResponseEntity<List<Ledger>> response = ledgerController.getAllLedgers(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(2, response.getBody().size());
        verify(ledgerDAO, times(1)).findByOwner(testUser);
    }


    @Test
    public void testGetAllLedgers_Unauthorized_NullPrincipal() {
        ResponseEntity<List<Ledger>> response = ledgerController.getAllLedgers(null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertNull(response.getBody());
    }

    @Test
    public void testGetAllLedgers_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<List<Ledger>> response = ledgerController.getAllLedgers(principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertNull(response.getBody());
    }

    @Test
    public void testGetAllLedgers_Success_EmptyList() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findByOwner(testUser)).thenReturn(List.of());

        ResponseEntity<List<Ledger>> response = ledgerController.getAllLedgers(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(0, response.getBody().size());
    }

    //getLedgerTransactionsForMonth tests
    @Test
    public void testGetLedgerTransactionsForMonth_Success_WithMonth() {
        YearMonth month = YearMonth.of(2025, 10);
        Transaction tx1 = new Expense(LocalDate.of(2025, 10, 5),
                BigDecimal.valueOf(50),
                "Lunch",
                null,
                testLedger,
                null);
        List<Transaction> transactions = List.of(tx1);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.findByLedgerIdAndOwnerId(
                        eq(1L), eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(transactions);

        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(1L, principal, month);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(1, response.getBody().size());
    }

    @Test
    public void testGetLedgerTransactionsForMonth_Success_WithoutMonth() {
        Transaction tx1 = new Expense(LocalDate.now(),
                BigDecimal.valueOf(50),
                "Lunch",
                null,
                testLedger,
                null);
        List<Transaction> transactions = List.of(tx1);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.findByLedgerIdAndOwnerId(
                        eq(1L),
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(transactions);

        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(1L, principal, null);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(1, response.getBody().size());
    }

    @Test
    public void testGetLedgerTransactionsForMonth_Unauthorized_NullPrincipal() {
        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(1L, null, YearMonth.now());

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetLedgerTransactionsForMonth_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(1L, principal, YearMonth.now());

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetLedgerTransactionsForMonth_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResponseStatusException.class, () ->
                ledgerController.getLedgerTransactionsForMonth(999L, principal, YearMonth.now())
        ); //se lancia l'eccezione, il test passa
    }

    @Test
    public void testGetLedgerTransactionsForMonth_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(1L, principal, YearMonth.now());

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetLedgerTransactionsForMonth_BadRequest_NullLedgerId() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<List<Transaction>> response = ledgerController.getLedgerTransactionsForMonth(null, principal, YearMonth.now());

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    //getLedgerCategories tests
    @Test
    public void testGetLedgerCategories_Success() {
        LedgerCategory parentCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        parentCategory.setId(1L);

        LedgerCategory subCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        subCategory.setId(2L);
        subCategory.setParent(parentCategory);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryDAO.findByLedgerIdAndParentIsNull(1L))
                .thenReturn(List.of(parentCategory));
        Mockito.when(ledgerCategoryDAO.findByParentId(1L))
                .thenReturn(List.of(subCategory));

        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals("Test Ledger", response.getBody().get("ledgerName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("categories");
        Assertions.assertEquals(1, categories.size());
        Assertions.assertEquals("Food", categories.get(0).get("CategoryName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subCategories = (List<Map<String, Object>>) categories.get(0).get("subCategories");
        Assertions.assertEquals(1, subCategories.size());
        Assertions.assertEquals("Lunch", subCategories.get(0).get("SubCategoryName"));
    }

    @Test
    public void testGetLedgerCategories_Success_NoSubCategories() {
        LedgerCategory parentCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        parentCategory.setId(1L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryDAO.findByLedgerIdAndParentIsNull(1L))
                .thenReturn(List.of(parentCategory));
        Mockito.when(ledgerCategoryDAO.findByParentId(1L))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("categories");
        Assertions.assertEquals(1, categories.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subCategories = (List<Map<String, Object>>) categories.get(0).get("subCategories");
        Assertions.assertEquals(0, subCategories.size());
    }

    @Test
    public void testGetLedgerCategories_Success_NoCategories() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryDAO.findByLedgerIdAndParentIsNull(1L))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("categories");
        Assertions.assertEquals(0, categories.size());
    }

    @Test
    public void testGetLedgerCategories_Unauthorized_NullPrincipal() {
        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetLedgerCategories_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetLedgerCategories_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResponseStatusException.class, () ->
                ledgerController.getLedgerCategories(999L, principal)
        );
    }

    @Test
    public void testGetLedgerCategories_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));


        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetLedgerCategories_Success_MultipleParentsWithSubCategories() {
        LedgerCategory food = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        food.setId(1L);

        LedgerCategory transport = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        transport.setId(2L);

        LedgerCategory lunch = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        lunch.setId(3L);

        LedgerCategory taxi = new LedgerCategory("Taxi", CategoryType.EXPENSE, testLedger);
        taxi.setId(4L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryDAO.findByLedgerIdAndParentIsNull(1L))
                .thenReturn(List.of(food, transport));
        Mockito.when(ledgerCategoryDAO.findByParentId(1L))
                .thenReturn(List.of(lunch));
        Mockito.when(ledgerCategoryDAO.findByParentId(2L))
                .thenReturn(List.of(taxi));

        ResponseEntity<Map<String, Object>> response = ledgerController.getLedgerCategories(1L, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("categories");
        Assertions.assertEquals(2, categories.size());
    }

    //getMonthlySummary tests
    @Test
    public void testGetMonthlySummary_Success_WithMonth() {
        YearMonth month = YearMonth.of(2025, 06);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.sumIncomeByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(5000));
        Mockito.when(transactionDAO.sumExpenseByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(3000));

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, month, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals("Test Ledger", response.getBody().get("ledgerName"));
        Assertions.assertEquals("2025-06", response.getBody().get("month"));
        Assertions.assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) response.getBody().get("totalIncome")));
        Assertions.assertEquals(0, BigDecimal.valueOf(3000).compareTo((BigDecimal) response.getBody().get("totalExpense")));
    }

    @Test
    public void testGetMonthlySummary_Success_WithoutMonth() {
        YearMonth currentMonth = YearMonth.now();

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.sumIncomeByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(5000));
        Mockito.when(transactionDAO.sumExpenseByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(3000));

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, null, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(currentMonth.toString(), response.getBody().get("month"));
    }

    @Test
    public void testGetMonthlySummary_Success_NullValues() {
        YearMonth month = YearMonth.of(2025, 10);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.sumIncomeByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(null);
        Mockito.when(transactionDAO.sumExpenseByLedgerAndPeriod(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(null);

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, month, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.getBody().get("totalIncome")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.getBody().get("totalExpense")));
    }

    @Test
    public void testGetMonthlySummary_Success_NoTransactions() {
        YearMonth month = YearMonth.of(2025, 10);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.sumIncomeByLedgerAndPeriod(
                        eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        Mockito.when(transactionDAO.sumExpenseByLedgerAndPeriod(
                        eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, month, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.getBody().get("totalIncome")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.getBody().get("totalExpense")));
    }

    @Test
    public void testGetMonthlySummary_Unauthorized_NullPrincipal() {
        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, YearMonth.now(), null);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetMonthlySummary_Unauthorized_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, YearMonth.now(), principal);

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testGetMonthlySummary_NotFound() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResponseStatusException.class, () ->
                ledgerController.getMonthlySummary(999L, YearMonth.now(), principal)
        );
    }

    @Test
    public void testGetMonthlySummary_Forbidden_NotOwner() {
        User anotherUser = new User("Bob", "password");
        anotherUser.setId(2L);
        testLedger.setOwner(anotherUser);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, YearMonth.now(), principal);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetMonthlySummary_BadRequest_NullLedgerId() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(null, YearMonth.now(), principal);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testGetMonthlySummary_Success_LargeValues() {
        YearMonth month = YearMonth.of(2025, 10);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerDAO.findById(1L)).thenReturn(Optional.of(testLedger));
        Mockito.when(transactionDAO.sumIncomeByLedgerAndPeriod(
                        eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(999999999.99));
        Mockito.when(transactionDAO.sumExpenseByLedgerAndPeriod(
                        eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(888888888.88));

        ResponseEntity<Map<String, Object>> response = ledgerController.getMonthlySummary(1L, month, principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(0, BigDecimal.valueOf(999999999.99)
                .compareTo((BigDecimal) response.getBody().get("totalIncome")));
        Assertions.assertEquals(0, BigDecimal.valueOf(888888888.88)
                .compareTo((BigDecimal) response.getBody().get("totalExpense")));
    }




}
