package com.ledger.project_software;

import com.ledger.project_software.Repository.LedgerCategoryRepository;
import com.ledger.project_software.Repository.LedgerRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.business.LedgerCategoryController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class LedgerCategoryControllerTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerCategoryRepository ledgerCategoryRepository;

    @InjectMocks
    private LedgerCategoryController ledgerCategoryController;

    private MockMvc mockMvc;
    private User testUser;
    private Ledger testLedger;
    private Account account1;
    private Account account2;

    @BeforeEach
    public void setup() {
        FormattingConversionService conversionService = new FormattingConversionService();

        conversionService.addConverter(new Converter<String, YearMonth>() {
            @Override
            public YearMonth convert(String source) {
                return YearMonth.parse(source, DateTimeFormatter.ofPattern("yyyy-MM"));
            }
        });

        mockMvc = MockMvcBuilders.standaloneSetup(ledgerCategoryController)
                .setConversionService(conversionService)
                .build();

        testUser = new User("Alice", "pas123");
        testUser.setId(1L);

        testLedger = new Ledger("Test Ledger", testUser);
        testLedger.setId(1L);

        account1 = new BasicAccount("Cash Account",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser);
        account1.setId(100L);


        account2 = new CreditAccount("Credit Card",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(0),
                null,
                null,
                AccountType.CREDIT_CARD);
        account2.setId(200L);

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetTransactionForMonth() throws Exception {
        LedgerCategory foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        foodCategory.setId(10L);

        LedgerCategory lunchCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        lunchCategory.setId(11L);
        lunchCategory.setParent(foodCategory);

        Transaction tx1 = new Expense(LocalDate.of(2025, 10, 5),
                BigDecimal.valueOf(10),
                null,
                account1,
                testLedger,
                foodCategory
        );

        Transaction tx2 = new Expense(LocalDate.of(2025, 10, 6),
                BigDecimal.valueOf(20),
                null,
                account2,
                testLedger,
                lunchCategory
        );
        List<Transaction> transactions = List.of(tx1, tx2);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerCategoryRepository.findById(eq(foodCategory.getId()))).thenReturn(Optional.of(foodCategory));
        Mockito.when(ledgerCategoryRepository.findById(eq(lunchCategory.getId()))).thenReturn(Optional.of(lunchCategory));
        Mockito.when(ledgerCategoryRepository.findByParentId(eq(foodCategory.getId()))).thenReturn(List.of(lunchCategory));
        Mockito.when(transactionRepository.findByCategoryIdsAndUserId(eq(List.of(10L, 11L)), any(), any(), eq(testUser.getId()))).thenReturn(transactions);
        Mockito.when(transactionRepository.findByCategoryIdAndUserId(eq(lunchCategory.getId()), any(), any(), eq(testUser.getId()))).thenReturn(List.of(tx2));

        mockMvc.perform(get("/ledger-categories/{id}/all-transactions-for-month", foodCategory.getId())
                        .param("month", "2025-10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(10))
                .andExpect(jsonPath("$[1].amount").value(20));

        mockMvc.perform(get("/ledger-categories/{id}/all-transactions-for-month", lunchCategory.getId())
                        .param("month", "2025-10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(20));

        mockMvc.perform(get("/ledger-categories/{id}/all-transactions-for-month", foodCategory.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(10))
                .andExpect(jsonPath("$[1].amount").value(20));

        mockMvc.perform(get("/ledger-categories/{id}/all-transactions-for-month", lunchCategory.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(20));

    }
}
