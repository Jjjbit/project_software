package com.ledger.project_software;

import com.ledger.project_software.Repository.AccountRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.business.AccountController;
import com.ledger.project_software.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.hamcrest.Matchers.is;
import org.springframework.core.convert.converter.Converter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class AccountControllerTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountController accountController;

    private MockMvc mockMvc;
    private User testUser;
    private Ledger testLedger;
    private List<Account> testAccounts;
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

        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setConversionService(conversionService)
                .build();

        testUser = new User("Alice", "pass123");
        testUser.setId(1L);

        testLedger = new Ledger("Alice's Ledger", testUser);

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
        //accountRepository.save(account2);
        account2.setId(200L);

        testAccounts =List.of(account1, account2);
    }


    @Test
    @WithMockUser(username = "Alice")
    public void testGetMyAccounts() throws Exception {
        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(accountRepository.findByOwner(Mockito.any(User.class))).thenReturn(testAccounts);

        mockMvc.perform(get("/accounts/all-accounts")
                        .principal(() -> "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Cash Account"))
                .andExpect(jsonPath("$[0].balance").value(1000))
                .andExpect(jsonPath("$[1].name").value("Credit Card"))
                .andExpect(jsonPath("$[1].balance").value(500));
    }

    @Test
    @WithMockUser(username = "Alice")
    public void testGetTransactionsForAccount() throws Exception {
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

        Mockito.when(userRepository.findByUsername("Alice")).thenReturn(testUser);
        Mockito.when(transactionRepository.findByAccountIdAndOwnerId(eq(account1.getId()), eq(testUser.getId()), any(), any()))
                .thenReturn(transactions);
        Mockito.when(transactionRepository.findByAccountIdAndOwnerId(eq(account2.getId()), eq(testUser.getId()), any(), any()))
                .thenReturn(transactions);
        Mockito.when(accountRepository.findById(account1.getId())).thenReturn(java.util.Optional.of(account1));
        Mockito.when(accountRepository.findById(account2.getId())).thenReturn(java.util.Optional.of(account2));

        mockMvc.perform(get("/accounts/{id}/get-transactions-for-month", account1.getId())
                        .principal(() -> "Alice")
                        .param("month", "2025-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].amount").value(50));

        mockMvc.perform(get("/accounts/{id}/get-transactions-for-month", account2.getId())
                        .principal(() -> "Alice")
                        .param("month", "2025-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].amount").value(50));
    }

}
