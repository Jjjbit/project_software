package com.ledger.project_software;

import com.ledger.project_software.business.UserService;
import com.ledger.project_software.domain.*;
import com.ledger.project_software.orm.UserDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class UserServiceStructuralTest {
    @Mock
    private UserDAO userDAO; //mock del repository per simulare il comportamento senza collegarsi al database

    @InjectMocks
    private UserService userService; //istanza del servizio con il mock iniettato


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
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(totalLending));
    }

    @Test
    public void testGetTotalBorrowing() {
        User testUser = new User("ALice", PasswordUtils.hash("pass123"));

        BorrowingAccount borrowing1 = new BorrowingAccount(
                "Borrow from Friend A",
                BigDecimal.valueOf(2000), //borrowing amount
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
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(totalBorrowing));
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
        assertEquals(0, BigDecimal.valueOf(7000).compareTo(totalAssets));
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
        assertEquals(0, BigDecimal.valueOf(60000).compareTo(totalLiabilities));
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
        assertEquals(0, BigDecimal.valueOf(10500).compareTo(netAssets));
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


        assertEquals(0, BigDecimal.valueOf(29000).compareTo(testUser.getTotalAssets()));

        //  4000 + 80000 + 2000 = 86000
        assertEquals(0, BigDecimal.valueOf(86000).compareTo(testUser.getTotalLiabilities()));

        assertEquals(0, BigDecimal.valueOf(-57000).compareTo(testUser.getNetAssets()));
    }

    @Test
    public void testEmptyAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));

        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLending()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalBorrowing()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalAssets()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLiabilities()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getNetAssets()));
    }

    @Test
    public void testRegister_Success() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(null);
        User newUser=userService.register("Alice", "pass123");

        assertNotNull(newUser);
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(any(User.class));
    }

    @Test
    public void testRegister_Failure_UsernameExists() {
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(new User("Alice", "123456789"));

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("Alice", "pass123");
        });
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, never()).save(any(User.class));

    }

    @Test
    public void testRegister_Failure_EmptyUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("", "pass123");
        });
    }

    @Test
    public void testRegister_Failure_EmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("Alice", "");
        });
    }

    @Test
    public void testRegister_Failure_ShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("Alice", "123");
        });
    }

    @Test
    public void testRegister_Failure_ShortUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("Al", "pass123");
        });
    }

    @Test
    public void testUpdateUserInfo_Success_UsernameOnly() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        userService.updateUserInfo(testUser, "NewAlice", null);

        assertEquals("NewAlice", testUser.getUsername());
        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).findByUsername("NewAlice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_BothUsernameAndPassword() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        userService.updateUserInfo(
                testUser,
                "NewAlice",
                "newPassword456"
        );

        assertEquals("NewAlice", testUser.getUsername());
        Assertions.assertTrue(PasswordUtils.verify("newPassword456", testUser.getPassword()));

        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).findByUsername("NewAlice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_NothingToUpdate() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);

        String originalPassword = testUser.getPassword();
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        userService.updateUserInfo(
                testUser,
                null,  // no username change
                null   // no password change
        );

        assertEquals("Alice", testUser.getUsername());
        assertEquals(originalPassword, testUser.getPassword());

        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).save(testUser);
    }

    @Test
    public void testUpdateUserInfo_Success_EmptyPassword() {
        User testUser = new User("Alice", PasswordUtils.hash("12345"));
        testUser.setId(1L);

        String originalPassword = testUser.getPassword();
        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(userDAO.save(any(User.class))).thenReturn(testUser);

        userService.updateUserInfo(
                testUser,
                "NewAlice",  // change username
                ""           // empty password, should not change
        );

        assertEquals("NewAlice", testUser.getUsername());
        assertEquals(originalPassword, testUser.getPassword()); // password unchanged

        verify(userDAO, times(1)).findByUsername("Alice");
        verify(userDAO, times(1)).findByUsername("NewAlice");
        verify(userDAO, times(1)).save(testUser);
    }
    @Test
    public void testUpdateUserInfo_UserIsNull() {

        assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUserInfo(
                    null,
                    "NewName",
                    "newPassword123"
            );
        });

        verify(userDAO, never()).findByUsername(any());
        verify(userDAO, never()).save(any());
    }

    @Test
    public void testUpdateUserInfo_NotFound_UserNotExists() {
        Mockito.when(userDAO.findByUsername("Bob")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUserInfo(
                    new User("Bob", PasswordUtils.hash("oldPassword")),
                    "NewBob",
                    "newPassword123"
            );
        });

        verify(userDAO, times(1)).findByUsername("Bob");
        verify(userDAO, never()).save(any());
    }

    @Test
    public void testGetUserAssets_Success_WithAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);

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

        Map<String, Object> response = userService.getUserAssets(testUser);

        assertEquals(5, response.size());
        Assertions.assertTrue(response.containsKey("totalAssets"));
        Assertions.assertTrue(response.containsKey("totalLiabilities"));
        Assertions.assertTrue(response.containsKey("netAssets"));
        Assertions.assertTrue(response.containsKey("totalLending"));
        Assertions.assertTrue(response.containsKey("totalBorrowing"));

        // totalAssets = cash(5000) + lending(2000) = 7000
        assertEquals(0, BigDecimal.valueOf(8000).compareTo((BigDecimal) response.get("totalAssets")));

        // totalLiabilities = creditCardDebt(3000) + borrowing(1000) = 4000
        assertEquals(0, BigDecimal.valueOf(4000).compareTo((BigDecimal) response.get("totalLiabilities")));

        // netAssets = 7000 - 4000 = 3000
        assertEquals(0, BigDecimal.valueOf(4000).compareTo((BigDecimal) response.get("netAssets")));

        // totalLending = 2000
        assertEquals(0, BigDecimal.valueOf(2000).compareTo((BigDecimal) response.get("totalLending")));

        // totalBorrowing = 1000
        assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) response.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_NoAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);

        Mockito.when(userDAO.findByUsername("Alice")).thenReturn(testUser);

        Map<String, Object> response = userService.getUserAssets(testUser);

        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLiabilities")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("netAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLending")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_NegativeNetAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);

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

        Map<String, Object> response = userService.getUserAssets(testUser);

        // totalAssets = 1000
        assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) response.get("totalAssets")));

        // totalLiabilities = 40000
        assertEquals(0, BigDecimal.valueOf(40000).compareTo((BigDecimal) response.get("totalLiabilities")));

        // netAssets = 1000 - 40000 = -39000
        assertEquals(0, BigDecimal.valueOf(-39000).compareTo((BigDecimal) response.get("netAssets")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_WithHiddenAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);

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

        Map<String, Object> response = userService.getUserAssets(testUser);

        assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) response.get("totalAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLiabilities")));
        assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) response.get("netAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLending")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

    @Test
    public void testGetUserAssets_Success_WithExcludedAccounts() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        testUser.setId(1L);

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

        Map<String, Object> response = userService.getUserAssets(testUser);

        assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) response.get("totalAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLiabilities")));
        assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) response.get("netAssets")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalLending")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) response.get("totalBorrowing")));

        verify(userDAO, times(1)).findByUsername("Alice");
    }

}
