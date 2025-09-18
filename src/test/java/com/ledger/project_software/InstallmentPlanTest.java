package com.ledger.project_software;

import com.ledger.project_software.Repository.AccountRepository;
import com.ledger.project_software.Repository.InstallmentPlanRepository;
import com.ledger.project_software.Repository.UserRepository;
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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.math.BigDecimal;

@SpringBootTest(classes = com.ledger.project_software.ProjectSoftwareApplication.class)
@Transactional
@AutoConfigureMockMvc
@Rollback
public class InstallmentPlanTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;

    private User testUser;
    private CreditAccount testAccount;
    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @BeforeEach
    public void setUp() {
        testUser = new User("Alice", "pass123");
        userRepository.save(testUser);

        testAccount = new CreditAccount("Test Account",
                BigDecimal.valueOf(1000),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000), // credit limit
                BigDecimal.valueOf(0), // current debt
                null,
                1,
                AccountType.CREDIT_CARD);
        accountRepository.save(testAccount);
        testUser.getAccounts().add(testAccount);
        userRepository.save(testUser);
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateInstallmentPlan_WithoutRateAndFeeStrategy() throws Exception {
        mockMvc.perform(post("/installment-plans/create")
                        .principal(() -> "Alice")
                        .param("linkedAccountId", String.valueOf(testAccount.getId()))
                        .param("totalAmount", "1200")
                        .param("totalPeriods", "12"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("installment plan created successfully"));

        InstallmentPlan newPlan = installmentPlanRepository.findAll().get(0);
        Assertions.assertNotNull(newPlan);
        Assertions.assertEquals(0, newPlan.getTotalAmount().compareTo(BigDecimal.valueOf(1200)));
        Assertions.assertEquals(12, newPlan.getTotalPeriods());
        Assertions.assertEquals(0, newPlan.getFeeRate().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, newPlan.getPaidPeriods());
        Assertions.assertEquals(InstallmentPlan.FeeStrategy.EVENLY_SPLIT, newPlan.getFeeStrategy());
        Assertions.assertEquals(testAccount.getId(), newPlan.getLinkedAccount().getId());

        CreditAccount updatedAccount = (CreditAccount) accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertTrue(updatedAccount.getInstallmentPlans().contains(newPlan));
        Assertions.assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(1200)));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1200)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testCreateInstallmentPlan_WithRateAndFeeStrategy() throws Exception {
        mockMvc.perform(post("/installment-plans/create")
                        .principal(() -> "Alice")
                        .param("linkedAccountId", String.valueOf(testAccount.getId()))
                        .param("totalAmount", "1200")
                        .param("totalPeriods", "12")
                        .param("feeRate", "0.2") //0.2%
                        .param("paidPeriods", "1")
                        .param("feeStrategy", "UPFRONT"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("installment plan created successfully"));

        InstallmentPlan newPlan = installmentPlanRepository.findAll().get(0);
        Assertions.assertNotNull(newPlan);
        Assertions.assertEquals(0, newPlan.getTotalAmount().compareTo(BigDecimal.valueOf(1200)));
        Assertions.assertEquals(12, newPlan.getTotalPeriods());
        Assertions.assertEquals(0, newPlan.getFeeRate().compareTo(BigDecimal.valueOf(0.2)));
        Assertions.assertEquals(1, newPlan.getPaidPeriods());
        Assertions.assertEquals(InstallmentPlan.FeeStrategy.UPFRONT, newPlan.getFeeStrategy());
        Assertions.assertEquals(testAccount.getId(), newPlan.getLinkedAccount().getId());
        Assertions.assertEquals(0, newPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(1100)));

        CreditAccount updatedAccount = (CreditAccount) accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertTrue(updatedAccount.getInstallmentPlans().contains(newPlan));
        // total debt should be totalAmount + fee
        Assertions.assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(1100)));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1100)));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditInstallmentPlan() throws Exception {
        InstallmentPlan plan = new InstallmentPlan(
                BigDecimal.valueOf(1200),
                12,
                BigDecimal.valueOf(0.1),
                1,
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                testAccount
        );
        installmentPlanRepository.save(plan);
        testAccount.addInstallmentPlan(plan);
        accountRepository.save(testAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/installment-plans/" + plan.getId() + "/edit")
                        .principal(() -> "Alice")
                        .param("totalAmount", "1500")
                        .param("totalPeriods", "15")
                        .param("paidPeriods", "3")
                        .param("feeRate", "0.2")
                        .param("feeStrategy", "UPFRONT"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("installment plan updated successfully"));

        InstallmentPlan updatedPlan = installmentPlanRepository.findById(plan.getId()).orElse(null);
        Assertions.assertEquals(0, updatedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(1500)));
        Assertions.assertEquals(15, updatedPlan.getTotalPeriods());
        Assertions.assertEquals(3, updatedPlan.getPaidPeriods());
        Assertions.assertEquals(0, updatedPlan.getFeeRate().compareTo(BigDecimal.valueOf(0.2)));
        Assertions.assertEquals(InstallmentPlan.FeeStrategy.UPFRONT, updatedPlan.getFeeStrategy());
        Assertions.assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(1200)));

        CreditAccount updatedAccount = (CreditAccount) accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(1200)));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1200)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testEditInstallmentPlan_changeAccount() throws Exception {
        Account newAccount = new CreditAccount("New Account",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(2000), // credit limit
                BigDecimal.valueOf(0), // current debt
                null,
                1,
                AccountType.CREDIT_CARD);
        accountRepository.save(newAccount);
        testUser.getAccounts().add(newAccount);
        userRepository.save(testUser);

        InstallmentPlan plan = new InstallmentPlan(
                BigDecimal.valueOf(1200),
                12,
                BigDecimal.valueOf(0.1),
                1,
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                testAccount
        );
        installmentPlanRepository.save(plan);
        testAccount.addInstallmentPlan(plan);
        accountRepository.save(testAccount);
        userRepository.save(testUser);

        mockMvc.perform(put("/installment-plans/" + plan.getId() + "/edit")
                        .principal(() -> "Alice")
                        .param("linkedAccountId", String.valueOf(newAccount.getId()))
                        .param("totalAmount", "1500")
                        .param("totalPeriods", "15")
                        .param("paidPeriods", "3")
                        .param("feeRate", "0.2")
                        .param("feeStrategy", "UPFRONT"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("installment plan updated successfully"));

        InstallmentPlan updatedPlan = installmentPlanRepository.findById(plan.getId()).orElse(null);
        Assertions.assertEquals(0, updatedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(1500)));
        Assertions.assertEquals(15, updatedPlan.getTotalPeriods());
        Assertions.assertEquals(3, updatedPlan.getPaidPeriods());
        Assertions.assertEquals(0, updatedPlan.getFeeRate().compareTo(BigDecimal.valueOf(0.2)));
        Assertions.assertEquals(InstallmentPlan.FeeStrategy.UPFRONT, updatedPlan.getFeeStrategy());
        Assertions.assertEquals(newAccount.getId(), updatedPlan.getLinkedAccount().getId());
        Assertions.assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(1200)));

        CreditAccount oldAccount = (CreditAccount) accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertFalse(oldAccount.getInstallmentPlans().contains(updatedPlan));
        Assertions.assertEquals(0, oldAccount.getCurrentDebt().compareTo(BigDecimal.ZERO));

        CreditAccount updatedAccount = (CreditAccount) accountRepository.findById(newAccount.getId()).orElse(null);
        Assertions.assertTrue(updatedAccount.getInstallmentPlans().contains(updatedPlan));
        Assertions.assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(1200)));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1200)));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testDeleteInstallmentPlan() throws Exception {
        InstallmentPlan plan = new InstallmentPlan(
                BigDecimal.valueOf(1200),
                12,
                BigDecimal.valueOf(0.1),
                1,
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                testAccount
        );
        installmentPlanRepository.save(plan);
        testAccount.addInstallmentPlan(plan);
        accountRepository.save(testAccount);
        userRepository.save(testUser);

        mockMvc.perform(delete("/installment-plans/" + plan.getId() + "/delete")
                        .principal(() -> "Alice"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("installment plan deleted successfully"));

        InstallmentPlan deletedPlan = installmentPlanRepository.findById(plan.getId()).orElse(null);
        Assertions.assertNull(deletedPlan);

        CreditAccount updatedAccount = (CreditAccount) accountRepository.findById(testAccount.getId()).orElse(null);
        Assertions.assertFalse(updatedAccount.getInstallmentPlans().contains(plan));
        Assertions.assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.ZERO));

        User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        Assertions.assertEquals(0, updatedUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
    }
}
