package com.ledger.project_software;

import com.ledger.project_software.business.AccountService;
import com.ledger.project_software.business.UserService;
import com.ledger.project_software.domain.*;
import com.ledger.project_software.orm.AccountDAO;
import com.ledger.project_software.orm.UserDAO;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
public class UserServiceTest {

    //functional test
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private UserService userService;
    @Autowired
    private AccountService accountService;


    @Test
    public void testRegisterAndVerifyInDatabase() {
        userService.register("newuser", "secure123");

        User registeredUser= userDAO.findByUsername("newuser");
        assertNotNull(registeredUser);
        assertEquals("newuser", registeredUser.getUsername());
        assertTrue(PasswordUtils.verify("secure123", registeredUser.getPassword()));
        assertEquals(1, userDAO.findAll().size());
        assertEquals(1, registeredUser.getLedgers().size());

        Ledger defaultLedger = registeredUser.getLedgers().get(0);
        assertEquals("Default Ledger", defaultLedger.getName());
        assertEquals(17, defaultLedger.getCategories().size());

    }

    @Test
    public void testRegisterDuplicateUsername(){
        userService.register("duplicate", "pass123");

        Exception exception=assertThrows(IllegalArgumentException.class, () -> {
            userService.register("duplicate", "pass123");
        });
        String expectedMessage="Username already exists";
        String actualMessage=exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testLoginWithCorrectCredentials() {
        userService.register("testuser", "securepassword");

        String response=userService.login("testuser", "securepassword");
        assertEquals("Login successful", response);
    }

    @Test
    public void testLoginWithIncorrectCredentials(){
        userService.register("testuser", "securepassword");

        Exception exception=assertThrows(IllegalArgumentException.class, () -> {
            userService.login("testuser", "wrongpassword");
        });

        String expectedMessage="Incorrect password";
        String actualMessage=exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testUpdateUserInfo_NewName() {
        User user=userService.register("user1", "oldpassword");

        userService.updateUserInfo(user, "updateduser", "newpassword");

        User updatedUser = userDAO.findByUsername("updateduser");
        assertNotNull(updatedUser);
        assertTrue(PasswordUtils.verify("newpassword", updatedUser.getPassword()));
        assertEquals(1, userDAO.findAll().size());
    }

    @Test
    public void testUpdateUserInfo_NewPassword() {
        User user = userService.register("user1", "oldpassword");

        userService.updateUserInfo(user, null, "newpassword");

        User updatedUser = userDAO.findByUsername("user1");
        assertNotNull(updatedUser);
        assertTrue(PasswordUtils.verify("newpassword", updatedUser.getPassword()));
    }

    @Test
    public void testUpdateUserInfo_NoChanges() {
        User user = userService.register("user1", "oldpassword");

        userService.updateUserInfo(user, null, null);

        User updatedUser = userDAO.findByUsername("user1");
        assertNotNull(updatedUser);
        assertTrue(PasswordUtils.verify("oldpassword", updatedUser.getPassword()));
    }


    @Test
    public void testGetUserAssets() {
        User testUser = userService.register("Alice", "pass123");
        accountService.createBasicAccount(testUser,
                "Cash Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS);

        accountService.createCreditAccount(testUser,
                "Credit Card",
                BigDecimal.valueOf(500), // balance
                null,
                true,
                true,
                BigDecimal.valueOf(1000), // creditLimit
                BigDecimal.valueOf(150), // currentDebt
                null, //billDate
                null, //dueDate
                AccountType.CREDIT_CARD);

        accountService.createLendingAccount(testUser,
                "Bob",
                BigDecimal.valueOf(300), // balance da ricevere
                null,
                true,
                true,
                null,
                LocalDate.now());

        accountService.createBorrowingAccount(testUser,
                "Mike",
                BigDecimal.valueOf(200), // balance da pagare
                null,
                true,
                true,
                null,
                LocalDate.now());

        Map<String, Object> response=userService.getUserAssets(testUser);
        Assertions.assertEquals(0, BigDecimal.valueOf(1800.00).compareTo((BigDecimal) response.get("totalAssets")));
        Assertions.assertEquals(0, BigDecimal.valueOf(350.00).compareTo((BigDecimal) response.get("totalLiabilities")));
        Assertions.assertEquals(0, BigDecimal.valueOf(1450.00).compareTo((BigDecimal) response.get("netAssets")));
        Assertions.assertEquals(0, BigDecimal.valueOf(300.00).compareTo((BigDecimal) response.get("totalLending")));
        Assertions.assertEquals(0,BigDecimal.valueOf(200.00).compareTo((BigDecimal) response.get("totalBorrowing")));

    }

}
