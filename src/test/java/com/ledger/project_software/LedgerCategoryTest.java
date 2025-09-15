package com.ledger.project_software;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.domain.*;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class LedgerCategoryTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Ledger testLedger1;
    private Ledger testLedger2;
    private User testUser;
    private Account testAccount;

    @BeforeEach
    public void setUp(){
        testUser=new User("Alice", "pass123", User.Role.USER);
        userRepository.save(testUser);

        testLedger1 = new Ledger("Test Ledger", testUser);
        ledgerRepository.save(testLedger1);
        testLedger2=new Ledger("Test Ledger2", testUser);
        ledgerRepository.save(testLedger2);

        testAccount = new BasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        accountRepository.save(testAccount);

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDemote() throws Exception{
        LedgerCategory foodCategory=new LedgerCategory("Food", CategoryType.EXPENSE, testLedger1);
        testLedger1.addCategoryComponent(foodCategory);
        ledgerCategoryRepository.save(foodCategory);

        LedgerCategory mealsCategory=new LedgerCategory("Meals", CategoryType.EXPENSE, testLedger1);
        testLedger1.addCategoryComponent(mealsCategory);
        ledgerCategoryRepository.save(mealsCategory);

        Transaction transaction1 = new Expense(LocalDate.now(), BigDecimal.valueOf(10),null, testAccount, testLedger1, foodCategory);
        transactionRepository.save(transaction1);
        testAccount.addTransaction(transaction1);
        testLedger1.addTransaction(transaction1);
        foodCategory.addTransaction(transaction1);

        accountRepository.save(testAccount);
        ledgerRepository.save(testLedger1);
        ledgerCategoryRepository.save(foodCategory);


        mockMvc.perform(put("/ledger-categories/"+ foodCategory.getId() +"/demote")
                        .principal(() -> "Alice")
                        .param("parentId", String.valueOf(mealsCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string("Demoted successfully"));

        LedgerCategory updateFoodCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Food");
        Assertions.assertEquals(mealsCategory.getId(), updateFoodCategory.getParent().getId());

        LedgerCategory updateMealsCategory=ledgerCategoryRepository.findByLedgerAndName(testLedger1, "Meals");
        Assertions.assertEquals(1, updateMealsCategory.getChildren().size());
        Assertions.assertEquals(1, updateMealsCategory.getTransactions().size());
        Assertions.assertEquals(foodCategory.getId(), updateMealsCategory.getChildren().get(0).getId());
    }
}
