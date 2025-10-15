package com.ledger.project_software;

import com.ledger.project_software.Repository.AccountRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.*;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class UserTest { // Integration test between UserController and UserRepository. collega a database reale
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void testRegisterAndVerifyInDatabase() throws Exception {
        mockMvc.perform(post("/users/register")
                        .param("username", "newuser")
                        .param("password", "secure123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Registration successful"));

        User savedUser = userRepository.findByUsername("newuser");
        Assertions.assertNotNull(savedUser);
        Assertions.assertTrue(PasswordUtils.verify("secure123", savedUser.getPassword()));
        Assertions.assertEquals(1, savedUser.getLedgers().size());
        Assertions.assertEquals("Default Ledger", savedUser.getLedgers().get(0).getName());
    }

    @Test
    public void testRegisterDuplicateUsername() throws Exception {
        User user=new User("duplicate", "pass123");
        userRepository.save(user);

        mockMvc.perform(post("/users/register")
                        .param("username", "duplicate")
                        .param("password", "pass123"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Username already exists"));

    }

    @Test
    public void testLoginWithCorrectCredentials() throws Exception {
        User user = new User("testuser", PasswordUtils.hash("securepassword"));
        userRepository.save(user);

        mockMvc.perform(post("/users/login")
                        .param("username", "testuser")
                        .param("password", "securepassword"))
                .andExpect(status().isOk())
                .andExpect(content().string("Login successful"));

        User existingUser = userRepository.findByUsername("testuser");
        Assertions.assertNotNull(existingUser);
        Assertions.assertTrue(PasswordUtils.verify("securepassword", existingUser.getPassword()));
    }

    @Test
    public void testLoginWithIncorrectCredentials() throws Exception {
        User user = new User("testuser", PasswordUtils.hash("securepassword"));
        userRepository.save(user);

        mockMvc.perform(post("/users/login")
                        .param("username", "testuser")
                        .param("password", "wrongpassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Password incorrect"));

        User existingUser = userRepository.findByUsername("testuser");
        Assertions.assertNotNull(existingUser);
        Assertions.assertTrue(PasswordUtils.verify("securepassword", existingUser.getPassword()));
    }

    @Test
    public void testUpdateUserInfo() throws Exception {
        User user = new User("olduser", PasswordUtils.hash("oldpassword"));
        userRepository.save(user);

        mockMvc.perform(post("/users/login")
                        .param("username", "olduser")
                        .param("password", "oldpassword"))
                .andExpect(status().isOk())
                .andExpect(content().string("Login successful"));

        mockMvc.perform(put("/users/me")
                        .param("username", "updateduser")
                        .param("password", "newpassword")
                        .principal(() -> "olduser"))
                .andExpect(status().isOk())
                .andExpect(content().string("User info updated"));

        User updatedUser = userRepository.findByUsername("updateduser");
        Assertions.assertNotNull(updatedUser);
        Assertions.assertTrue(PasswordUtils.verify("newpassword", updatedUser.getPassword()));
        Assertions.assertEquals(1, userRepository.findAll().size());
    }

    @Test
    public void testGetUserAssets() throws Exception {
        User testUser = new User("Alice", PasswordUtils.hash("pass123"));
        userRepository.save(testUser);

        Account account1 = new BasicAccount("Cash Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        testUser.getAccounts().add(account1);
        accountRepository.save(account1);

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
        accountRepository.save(account2);

        Account account3= new LendingAccount("Bob",
                BigDecimal.valueOf(300), // balance da ricevere
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        testUser.getAccounts().add(account3);
        accountRepository.save(account3);

        Account account4 = new BorrowingAccount("Mike",
                BigDecimal.valueOf(200), // balance da pagare
                null,
                true,
                true,
                testUser,
                LocalDate.now());
        testUser.getAccounts().add(account4);
        accountRepository.save(account4);

        mockMvc.perform(post("/users/login")
                        .param("username", "Alice")
                        .param("password", "pass123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Login successful"));

        mockMvc.perform(get("/users/my-assets")
                        .principal(() -> "Alice"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets").value(1800))
                .andExpect(jsonPath("$.totalLiabilities").value(350))
                .andExpect(jsonPath("$.netAssets").value(1450))
                .andExpect(jsonPath("$.totalLending").value(300))
                .andExpect(jsonPath("$.totalBorrowing").value(200));
    }

}
