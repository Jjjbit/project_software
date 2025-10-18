package com.ledger.project_software;

import com.ledger.project_software.orm.UserDAO;
import com.ledger.project_software.business.UserController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class UserStructuralTest {
    @Mock
    private UserDAO userDAO; //mock del repository per simulare il comportamento senza collegarsi al database

    @InjectMocks
    private UserController userController;


    @Test
    public void testGetTotalLending() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        LendingAccount lending1 = new LendingAccount(
                "Lend to Friend A",
                BigDecimal.valueOf(1000),
                null,
                true,  // includedInNetAsset
                true, //selected
                testUser,
                LocalDate.now()
        );

        LendingAccount lending2 = new LendingAccount(
                "Lend to Friend B",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );


        LendingAccount lending3 = new LendingAccount(
                "Excluded Lending",
                BigDecimal.valueOf(300),
                null,
                false, // not includedInNetAsset
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: hidden account
        LendingAccount lending4 = new LendingAccount(
                "Hidden Lending",
                BigDecimal.valueOf(200),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        lending4.hide(); // hide this account

        // Edge case: zero balance
        LendingAccount lending5 = new LendingAccount(
                "Zero Balance Lending",
                BigDecimal.ZERO,
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        testUser.getAccounts().add(lending1);
        testUser.getAccounts().add(lending2);
        testUser.getAccounts().add(lending3);
        testUser.getAccounts().add(lending4);
        testUser.getAccounts().add(lending5);



        BigDecimal totalLending = testUser.getTotalLending();
        Assertions.assertEquals(0, BigDecimal.valueOf(1500).compareTo(totalLending));
    }

    @Test
    public void testGetTotalBorrowing() {
        User testUser = new User("ALice", PasswordUtils.hash("pass123"));

        BorrowingAccount borrowing1 = new BorrowingAccount(
                "Borrow from Friend A",
                BigDecimal.valueOf(2000),
                null,
                true,  // includedInNetAsset
                true, // selectable
                testUser,
                LocalDate.now()
        );

        BorrowingAccount borrowing2 = new BorrowingAccount(
                "Borrow from Friend B",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: excluded from net worth
        BorrowingAccount borrowing3 = new BorrowingAccount(
                "Excluded Borrowing",
                BigDecimal.valueOf(500),
                null,
                false, // NOT includedInNetWorth
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: hidden account
        BorrowingAccount borrowing4 = new BorrowingAccount(
                "Hidden Borrowing",
                BigDecimal.valueOf(300),
                null,
                true,
                true, // hidden
                testUser,
                LocalDate.now()
        );
        borrowing4.hide(); // hide this account

        testUser.getAccounts().add(borrowing1);
        testUser.getAccounts().add(borrowing2);
        testUser.getAccounts().add(borrowing3);
        testUser.getAccounts().add(borrowing4);


        // only borrowing1 and borrowing2 should be counted
        BigDecimal totalBorrowing = testUser.getTotalBorrowing();
        Assertions.assertEquals(0, BigDecimal.valueOf(3000).compareTo(totalBorrowing));
    }

    @Test
    public void testGetTotalAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(5000),
                null,
                true,
                false,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );


        // create LendingAccount
        LendingAccount lending = new LendingAccount(
                "Lend to Friend",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // create CreditAccount (with currentDebt, should not count as asset)
        CreditAccount creditCard = new CreditAccount(
                "Credit Card",
                BigDecimal.valueOf(500), // balance
                testUser,
                null,
                false,
                true,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(9500), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        // create LoanAccount (should not count as asset)
        LoanAccount loan = new LoanAccount(
                "Mortgage",
                testUser,
                null,
                false,
                36,
                0,
                BigDecimal.ZERO, //interestRate
                BigDecimal.valueOf(100000), //loanAmount
                creditCard, //receving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create BorrowingAccount (should not count as asset)
        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrow from Friend",
                BigDecimal.valueOf(1000),
                null,
                false,
                true,
                testUser,
                LocalDate.now()
        );


        BasicAccount hiddenAccount = new BasicAccount(
                "Hidden Cash",
                BigDecimal.valueOf(3000),
                null,
                true,
                true, // hidden
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        hiddenAccount.hide(); // hide this account

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(lending);
        testUser.getAccounts().add(creditCard);
        testUser.getAccounts().add(loan);
        testUser.getAccounts().add(borrowing);
        testUser.getAccounts().add(hiddenAccount);

        // total assets should be cash + lending = 5000 + 2000 = 7000
        BigDecimal totalAssets = testUser.getTotalAssets();
        Assertions.assertEquals(0, BigDecimal.valueOf(7000).compareTo(totalAssets));
    }

    @Test
    public void testGetTotalLiabilities() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        // create CreditAccounts (with currentDebt)
        CreditAccount creditCard1 = new CreditAccount(
                "Credit Card 1",
                BigDecimal.valueOf(1000), // balance
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(3000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        CreditAccount creditCard2 = new CreditAccount(
                "Credit Card 2",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(5000), // creditLimit
                BigDecimal.valueOf(2000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        //create active LoanAccount
        LoanAccount activeLoan = new LoanAccount(
                "Car Loan",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.ZERO, // interestRate
                BigDecimal.valueOf(50000), // loanAmount
                creditCard1, // receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create ended LoanAccount (should not count)
        LoanAccount endedLoan = new LoanAccount(
                "Ended Loan",
                testUser,
                null,
                true,
                36,
                36,
                BigDecimal.ZERO, // interestRate
                BigDecimal.valueOf(20000), // loanAmount
                creditCard2, // receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create BorrowingAccount (should count)
        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrow from Friend",
                BigDecimal.valueOf(5000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()

        );

        // create hidden CreditAccount (should not count)
        CreditAccount hiddenCreditCard = new CreditAccount(
                "Hidden Credit Card",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(1000),
                null,
                null,
                AccountType.CREDIT_CARD
        );
        hiddenCreditCard.hide(); // hide this account

        testUser.getAccounts().add(creditCard1);
        testUser.getAccounts().add(creditCard2);
        testUser.getAccounts().add(activeLoan);
        testUser.getAccounts().add(endedLoan);
        testUser.getAccounts().add(borrowing);
        testUser.getAccounts().add(hiddenCreditCard);


        // totalLiabilities = creditCard1Debt(3000) + creditCard2Debt(2000) + activeLoan(50000) + borrowing(5000) = 60000
        BigDecimal totalLiabilities = testUser.getTotalLiabilities();
        Assertions.assertEquals(0, BigDecimal.valueOf(60000).compareTo(totalLiabilities));
    }

    @Test
    public void testGetNetAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(10000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        LendingAccount lending = new LendingAccount(
                "Lending",
                BigDecimal.valueOf(5000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // create CreditAccount with currentDebt
        CreditAccount creditCard = new CreditAccount(
                "Credit Card",
                BigDecimal.valueOf(500), // balance
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(3000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrowing",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(lending);
        testUser.getAccounts().add(creditCard);
        testUser.getAccounts().add(borrowing);



        BigDecimal netAssets = testUser.getNetAssets();
        Assertions.assertEquals(0, BigDecimal.valueOf(10500).compareTo(netAssets));
    }

    @Test
    public void testNetAssetsWithAllAccountTypes() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        BasicAccount cash = new BasicAccount("Cash", BigDecimal.valueOf(5000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        BasicAccount savings = new BasicAccount("Savings", BigDecimal.valueOf(20000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        LendingAccount lending = new LendingAccount("Lending", BigDecimal.valueOf(3000),
                 null, true, true, testUser,LocalDate.now());

        //create account with debt
        CreditAccount creditCard = new CreditAccount("Credit Card", BigDecimal.valueOf(1000),
                testUser, null, true, false, BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000), null, null, AccountType.CREDIT_CARD);
        LoanAccount loan = new LoanAccount("Mortgage", testUser, null,
                true, 36, 0, BigDecimal.ZERO, BigDecimal.valueOf(80000), creditCard,
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST);
        BorrowingAccount borrowing = new BorrowingAccount("Borrowing", BigDecimal.valueOf(2000),
                null, true, true, testUser, LocalDate.now());

        testUser.getAccounts().addAll(List.of(cash, savings, lending, creditCard, loan, borrowing));


        Assertions.assertEquals(0, BigDecimal.valueOf(29000).compareTo(testUser.getTotalAssets()));

        //  4000 + 80000 + 2000 = 86000
        Assertions.assertEquals(0, BigDecimal.valueOf(86000).compareTo(testUser.getTotalLiabilities()));

        Assertions.assertEquals(0, BigDecimal.valueOf(-57000).compareTo(testUser.getNetAssets()));
    }

    @Test
    public void testEmptyAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLending()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalBorrowing()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalAssets()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLiabilities()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getNetAssets()));
    }

    @Test
    public void testLogin_Success() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = userController.login("Alice", "12345");

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Login successful", response.getBody());
        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testLogin_Failure_WrongPassword() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<String> response = userController.login("Alice", "anyPassword");

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Password incorrect", response.getBody());
    }

    @Test
    public void testLogin_Failure_UserNotFound() {
        Mockito.when(userDAO.findByUsername("Bob")).thenReturn(null);

        ResponseEntity<String> response = userController.login("Bob", "anyPassword");

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("User not found", response.getBody());
    }

    @Test
    public void testRegister_Success() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);
        ResponseEntity<String> response = userController.register("Alice", "pass123");

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Registration successful", response.getBody());
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(any(User.class));
    }

    @Test
    public void testRegister_Failure_UsernameExists() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(new User("Alice", "123456789"));

        ResponseEntity<String> response = userController.register("Alice", "pass123");

        Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Assertions.assertEquals("Username already exists", response.getBody());
    }

    @Test
    public void testRegister_Failure_EmptyUsername() {
        ResponseEntity<String> response = userController.register("", "pass123");
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Username cannot be empty", response.getBody());
    }

    @Test
    public void testRegister_Failure_EmptyPassword() {
        ResponseEntity<String> response = userController.register("Alice", "");
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Password cannot be empty", response.getBody());
    }

    @Test
    public void testRegister_Failure_ShortPassword() {
        ResponseEntity<String> response = userController.register("Alice", "123");
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Password must be at least 6 characters long", response.getBody());
    }

    @Test
    public void testRegister_Failure_ShortUsername() {
        ResponseEntity<String> response = userController.register("Al", "pass123");
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("Username must be between 3 and 20 characters long", response.getBody());
    }

    @Test
    public void testUpdateUserInfo_Success_UsernameOnly() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<String> response = userController.updateUserInfo(
                principal,
                "NewAlice",
                null
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("User info updated", response.getBody());
        Assertions.assertEquals("NewAlice", testUser.getUsername());
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_BothUsernameAndPassword() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<String> response = userController.updateUserInfo(
                principal,
                "NewAlice",
                "newPassword456"
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("User info updated", response.getBody());
        Assertions.assertEquals("NewAlice", testUser.getUsername());
        Assertions.assertTrue(PasswordUtils.verify("newPassword456", testUser.getPassword()));

        // Verify that repository methods were called
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_NothingToUpdate() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        String originalPassword = testUser.getPassword();
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<String> response = userController.updateUserInfo(
                principal,
                null,
                null
        );

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("User info updated", response.getBody());
        Assertions.assertEquals("Alice", testUser.getUsername());
        Assertions.assertEquals(originalPassword, testUser.getPassword());

        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_EmptyPassword() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        String originalPassword = testUser.getPassword();
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        ResponseEntity<String> response = userController.updateUserInfo(
                principal,
                "NewAlice",
                ""  // empty password
        );


        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("NewAlice", testUser.getUsername());
        Assertions.assertEquals(originalPassword, testUser.getPassword()); // password unchanged

        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Unauthorized_NullPrincipal() {
        ResponseEntity<String> response = userController.updateUserInfo(
                null,
                "NewAlice",
                "newPassword"
        );


        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertEquals("Not logged in", response.getBody());

        verify(userDAO, never()).findByUsername(any());
        verify(userDAO, never()).save(any());
    }

    @Test
    public void testUpdateUserInfo_NotFound_UserNotExists() {
        Principal principal = () -> "Bob";
        Mockito.when(userDAO.findByUsername("Bob")).thenReturn(null);

        ResponseEntity<String> response = userController.updateUserInfo(
                principal,
                "NewAlice",
                "newPassword"
        );

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertEquals("User not found", response.getBody());

        verify(userDAO, times(1)).findByUsername("Bob");
        verify(userDAO, never()).save(any());
    }

    @Test
    public void testGetUserAssets_Success_WithAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(5000),
                null, true, false,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        LendingAccount lending = new LendingAccount(
                "Lending",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        CreditAccount creditCard = new CreditAccount(
                "Credit Card",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(3000), // currentDebt
                null, null,
                AccountType.CREDIT_CARD
        );

        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrowing",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(lending);
        testUser.getAccounts().add(creditCard);
        testUser.getAccounts().add(borrowing);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        Assertions.assertEquals(5, body.size());
        Assertions.assertTrue(body.containsKey("totalAssets"));
        Assertions.assertTrue(body.containsKey("totalLiabilities"));
        Assertions.assertTrue(body.containsKey("netAssets"));
        Assertions.assertTrue(body.containsKey("totalLending"));
        Assertions.assertTrue(body.containsKey("totalBorrowing"));

        // totalAssets = cash(5000) + lending(2000) = 7000
        Assertions.assertEquals(0, BigDecimal.valueOf(8000).compareTo((BigDecimal) body.get("totalAssets")));

        // totalLiabilities = creditCardDebt(3000) + borrowing(1000) = 4000
        Assertions.assertEquals(0, BigDecimal.valueOf(4000).compareTo((BigDecimal) body.get("totalLiabilities")));

        // netAssets = 7000 - 4000 = 3000
        Assertions.assertEquals(0, BigDecimal.valueOf(4000).compareTo((BigDecimal) body.get("netAssets")));

        // totalLending = 2000
        Assertions.assertEquals(0, BigDecimal.valueOf(2000).compareTo((BigDecimal) body.get("totalLending")));

        // totalBorrowing = 1000
        Assertions.assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) body.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_NoAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);


        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) body.get("totalAssets")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) body.get("totalLiabilities")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) body.get("netAssets")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) body.get("totalLending")));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) body.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_NegativeNetAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(1000),
                null, true, false,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        LoanAccount loan = new LoanAccount(
                "Loan",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.ZERO,
                BigDecimal.valueOf(40000), // loanAmount
                cash, //receving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(loan);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();

        // totalAssets = 1000
        Assertions.assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) body.get("totalAssets")));

        // totalLiabilities = 40000
        Assertions.assertEquals(0, BigDecimal.valueOf(40000).compareTo((BigDecimal) body.get("totalLiabilities")));

        // netAssets = 1000 - 40000 = -39000 (负数)
        Assertions.assertEquals(0, BigDecimal.valueOf(-39000).compareTo((BigDecimal) body.get("netAssets")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Unauthorized_NullPrincipal() {

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(null);


        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertNull(response.getBody());
    }

    @Test
    public void testGetUserAssets_NotFound_UserNotExists() {
        Principal principal = () -> "Alice";
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);


        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);


        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Assertions.assertNull(response.getBody());
    }

    @Test
    public void testGetUserAssets_Success_WithHiddenAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(5000),
                null, true, false,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        BasicAccount hiddenCash = new BasicAccount(
                "Hidden Cash",
                BigDecimal.valueOf(3000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        hiddenCash.hide(); // hide this account

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(hiddenCash);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();

        Assertions.assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) body.get("totalAssets")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_WithExcludedAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);
        Principal principal = () -> "Alice";

        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(5000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        cash.setIncludedInNetAsset(true);

        BasicAccount excludedCash = new BasicAccount(
                "Excluded Cash",
                BigDecimal.valueOf(2000),
                null,
                false,  // includedInNetAsset = false
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(excludedCash);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userController.getUserAssets(principal);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();

        Assertions.assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) body.get("totalAssets")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

}
