package com.ledger.project_software;

import com.ledger.project_software.business.UserController;
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
    private AccountDAO accountDAO;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private UserService userService;


    @Test
    public void testRegisterAndVerifyInDatabase() {
        userService.register("newuser", "secure123");

        assertNotNull(userDAO.findByUsername("newuser"));
    }

    @Test
    public void testRegisterDuplicateUsername(){
        User user=new User("duplicate", "pass123");
        userDAO.save(user);

        assertThrows(IllegalArgumentException.class, () -> {
            userService.register("duplicate", "pass123");
        });
    }

    @Test
    public void testLoginWithCorrectCredentials()  {
        User user = new User("testuser", PasswordUtils.hash("securepassword"));
        userDAO.save(user);

        String response=userService.login("testuser", "securepassword");
        Assertions.assertEquals("Login successful", response);
    }

    @Test
    public void testLoginWithIncorrectCredentials(){
        User user = new User("testuser", PasswordUtils.hash("securepassword"));
        userDAO.save(user);

        assertThrows(IllegalArgumentException.class, () -> {
            userService.login("testuser", "wrongpassword");
        });

        User existingUser = userDAO.findByUsername("testuser");
        Assertions.assertNotNull(existingUser);
        Assertions.assertTrue(PasswordUtils.verify("securepassword", existingUser.getPassword()));
    }

    @Test
    public void testUpdateUserInfo_NewName() {
        User user = new User("olduser", PasswordUtils.hash("oldpassword"));
        userDAO.save(user);

        userService.updateUserInfo(user, "updateduser", "newpassword");

        User updatedUser = userDAO.findByUsername("updateduser");
        Assertions.assertNotNull(updatedUser);
        Assertions.assertTrue(PasswordUtils.verify("newpassword", updatedUser.getPassword()));
        Assertions.assertEquals(1, userDAO.findAll().size());
    }

    @Test
    public void testUpdateUserInfo_NewPassword() {
        User user = new User("user1", PasswordUtils.hash("oldpassword"));
        userDAO.save(user);

        userService.updateUserInfo(user, null, "newpassword");

        User updatedUser = userDAO.findByUsername("user1");
        Assertions.assertNotNull(updatedUser);
        Assertions.assertTrue(PasswordUtils.verify("newpassword", updatedUser.getPassword()));
    }

    @Test
    public void testUpdateUserInfo_NoChanges() {
        User user = new User("user1", PasswordUtils.hash("oldpassword"));
        userDAO.save(user);

        userService.updateUserInfo(user, null, null);

        User updatedUser = userDAO.findByUsername("user1");
        Assertions.assertNotNull(updatedUser);
        Assertions.assertTrue(PasswordUtils.verify("oldpassword", updatedUser.getPassword()));
    }


    @Test
    public void testGetUserAssets() {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        userDAO.save(testUser);

        Account account1 = new BasicAccount("Cash Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        testUser.getAccounts().add(account1);
        accountDAO.save(account1);

        Account account2 = new CreditAccount("Credit Card",
                BigDecimal.valueOf(500), // balance
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000), // creditLimit
                BigDecimal.valueOf(150), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD);
        testUser.getAccounts().add(account2);
        accountDAO.save(account2);

        Account account3= new LendingAccount("Bob",
                BigDecimal.valueOf(300), // balance da ricevere
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        testUser.getAccounts().add(account3);
        accountDAO.save(account3);

        Account account4 = new BorrowingAccount("Mike",
                BigDecimal.valueOf(200), // balance da pagare
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        testUser.getAccounts().add(account4);
        accountDAO.save(account4);

        Map<String, Object> response=userService.getUserAssets(testUser);
        Assertions.assertEquals(0, BigDecimal.valueOf(1800.00).compareTo((BigDecimal)  response.get("totalAssets")));
        Assertions.assertEquals(0, BigDecimal.valueOf(350.00).compareTo((BigDecimal) response.get("totalLiabilities")));
        Assertions.assertEquals(0, BigDecimal.valueOf(1450.00).compareTo((BigDecimal) response.get("netAssets")));
        Assertions.assertEquals(0, BigDecimal.valueOf(300.00).compareTo((BigDecimal) response.get("totalLending")));
        Assertions.assertEquals(0,BigDecimal.valueOf(200.00).compareTo((BigDecimal) response.get("totalBorrowing")));

    }

}
