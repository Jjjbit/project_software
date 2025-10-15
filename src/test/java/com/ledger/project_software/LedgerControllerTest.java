package com.ledger.project_software;

import com.ledger.project_software.Repository.LedgerCategoryRepository;
import com.ledger.project_software.Repository.LedgerRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.business.LedgerController;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class LedgerControllerTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private LedgerCategoryRepository ledgerCategoryRepository;

    @InjectMocks
    private LedgerController ledgerController;


    private MockMvc mockMvc;
    private User testUser;
    private Ledger testLedger;
    private Ledger testLedger1;
    private List<Ledger> testLedgers;
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

        mockMvc = MockMvcBuilders.standaloneSetup(ledgerController)
                .setConversionService(conversionService)
                .build();

        testUser = new User("Alice", "pas123");
        testUser.setId(1L);

        testLedger = new Ledger("Test Ledger", testUser);
        testLedger.setId(10L);
        testUser.getLedgers().add(testLedger);
        testLedger1 = new Ledger("Another Ledger", testUser);
        testLedger1.setId(20L);
        testUser.getLedgers().add(testLedger1);

        testLedgers=testUser.getLedgers();

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
    public void testGetAllLedgers() throws Exception {
        Mockito.when(userRepository.findByUsername("Alice"))
                .thenReturn(testUser);
        Mockito.when(ledgerRepository.findByOwner(Mockito.any(User.class)))
                .thenReturn(testLedgers);

        mockMvc.perform(get("/ledgers/all-ledgers")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Default Ledger"))
                .andExpect(jsonPath("$[1].name").value("Test Ledger"))
                .andExpect(jsonPath("$[2].name").value("Another Ledger"));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetLedgerTransactions() throws Exception {
        Transaction tx1 = new Transfer(LocalDate.of(2025, 10, 5),
                null,
                account1,
                account2,
                BigDecimal.valueOf(100),
                testLedger
        );

        Transaction tx2 = new Transfer(LocalDate.of(2025, 10, 5),
                null,
                account2,
                account1,
                BigDecimal.valueOf(50),
                testLedger
        );

        List<Transaction> transactions = List.of(tx1, tx2);

        Mockito.when(transactionRepository.findByLedgerIdAndOwnerId(
                eq(testLedger.getId()),
                eq(testUser.getId()),
                any(),
                any()
        )).thenReturn(transactions);

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(eq(testLedger.getId()))).thenReturn(java.util.Optional.of(testLedger));

        mockMvc.perform(get("/ledgers/{ledgerId}/all-transactions-for-month", testLedger.getId())
                        .principal(() -> "Alice")
                        .param("month", "2025-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].amount").value(50));

    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetLedgerCategories() throws Exception {
        LedgerCategory foodCategory = new LedgerCategory("Food", CategoryType.EXPENSE, testLedger);
        foodCategory.setId(10L);
        LedgerCategory transportCategory = new LedgerCategory("Transport", CategoryType.EXPENSE, testLedger);
        transportCategory.setId(20L);
        LedgerCategory lunchCategory = new LedgerCategory("Lunch", CategoryType.EXPENSE, testLedger);
        lunchCategory.setId(11L);


        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(eq(testLedger.getId())))
                .thenReturn(Optional.of(testLedger));
        Mockito.when(ledgerCategoryRepository.findByLedgerIdAndParentIsNull(eq(testLedger.getId())))
                .thenReturn(List.of(foodCategory, transportCategory));
        Mockito.when(ledgerCategoryRepository.findByParentId(eq(foodCategory.getId())))
                .thenReturn(List.of(lunchCategory));

        mockMvc.perform(get("/ledgers/{ledgerId}/categories", testLedger.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].CategoryName").value("Food"))
                .andExpect(jsonPath("$.categories[1].CategoryName").value("Transport"))
                .andExpect(jsonPath("$.categories[0].subCategories[0].SubCategoryName").value("Lunch"));;
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetMonthlySummary() throws Exception {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(ledgerRepository.findById(eq(testLedger.getId()))).thenReturn(Optional.of(testLedger));

        Mockito.when(transactionRepository.sumIncomeByLedgerAndPeriod(eq(testLedger.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(5000));
        Mockito.when(transactionRepository.sumExpenseByLedgerAndPeriod(eq(testLedger.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(3200));

        mockMvc.perform(get("/ledgers/{ledgerId}/monthly-summary", testLedger.getId())
                        .param("month", "2025-10")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.month").value("2025-10"))
                .andExpect(jsonPath("$.totalIncome").value(5000))
                .andExpect(jsonPath("$.totalExpense").value(3200));

        mockMvc.perform(get("/ledgers/{ledgerId}/monthly-summary", testLedger.getId())
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerName").value("Test Ledger"))
                .andExpect(jsonPath("$.month").value(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))))
                .andExpect(jsonPath("$.totalIncome").value(5000))
                .andExpect(jsonPath("$.totalExpense").value(3200));
    }


}
