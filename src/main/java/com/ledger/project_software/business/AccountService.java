package com.ledger.project_software.business;

import com.ledger.project_software.domain.*;
import com.ledger.project_software.orm.AccountDAO;
import com.ledger.project_software.orm.LedgerDAO;
import com.ledger.project_software.orm.TransactionDAO;
import com.ledger.project_software.orm.UserDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class AccountService {

    private final AccountDAO accountDAO;
    private final UserDAO userDAO;
    private final TransactionDAO transactionDAO;
    private final LedgerDAO ledgerDAO;
    public AccountService(AccountDAO accountDAO, UserDAO userDAO,
                          TransactionDAO transactionDAO, LedgerDAO ledgerDAO) {
        this.accountDAO = accountDAO;
        this.userDAO = userDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerDAO = ledgerDAO;
    }

    @Transactional
    public BasicAccount createBasicAccount(User user,
                                           String accountName,
                                           BigDecimal balance,
                                           String note,
                                           boolean includedInNetWorth,
                                           boolean selectable,
                                           AccountType type,
                                           AccountCategory category) {
        validateUser(user);

        if (category == AccountCategory.CREDIT || category == AccountCategory.VIRTUAL_ACCOUNT) {
            throw new IllegalArgumentException("Invalid account type for basic account");
        }
        if(balance.compareTo(BigDecimal.ZERO)<0){
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        BasicAccount account = new BasicAccount(accountName,
                balance, note,
                includedInNetWorth,
                selectable,
                type,
                category,
                user);
        accountDAO.save(account);
        user.getAccounts().add(account);
        userDAO.save(user);

        return account;
    }

    @Transactional
    public CreditAccount createCreditAccount(User user,
                                             String accountName,
                                             BigDecimal balance,
                                             String note,
                                             boolean includedInNetWorth,
                                             boolean selectable,
                                             BigDecimal creditLimit,
                                             BigDecimal currentDebt,
                                             Integer billDate,
                                             Integer dueDate,
                                             AccountType type) {
        validateUser(user);

        if (type != AccountType.CREDIT_CARD) {
            throw new IllegalArgumentException("Invalid account type for credit account");
        }

        CreditAccount account = new CreditAccount(accountName, balance, user, note,
                includedInNetWorth, selectable, creditLimit,
                currentDebt, billDate, dueDate, type);
        accountDAO.save(account);
        user.getAccounts().add(account);
        userDAO.save(user);

        return account;
    }

    @Transactional
    public LoanAccount createLoanAccount(User user,
                                         String accountName,
                                         String note,
                                         boolean includedInNetAsset,
                                         int totalPeriods,
                                         int repaidPeriods,
                                         BigDecimal annualInterestRate,
                                         BigDecimal loanAmount,
                                         Long receivingAccountId,
                                         LocalDate repaymentDate,
                                         LoanAccount.RepaymentType repaymentType) {
        validateUser(user);

        Account receivingAccount = null;
        if (receivingAccountId != null) {
            receivingAccount = accountDAO.findById(receivingAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Receiving account not found"));
            validateAccountOwnership(receivingAccount, user);
        }

        LoanAccount account = new LoanAccount(
                accountName,
                user,
                note,
                includedInNetAsset,
                totalPeriods,
                repaidPeriods,
                annualInterestRate,
                loanAmount,
                receivingAccount,
                repaymentDate,
                repaymentType);

        Transaction tx = new Transfer(LocalDate.now(),
                "Loan disbursement",
                account,
                receivingAccount,
                loanAmount,
                null);
        account.getOutgoingTransactions().add(tx);
        accountDAO.save(account);

        if(receivingAccount != null){
            receivingAccount.credit(loanAmount);
            receivingAccount.getIncomingTransactions().add(tx);
            accountDAO.save(receivingAccount);
        }

        user.getAccounts().add(account);
        userDAO.save(user);

        return account;
    }

    @Transactional
    public BorrowingAccount createBorrowingAccount(User user,
                                                   String name,
                                                   BigDecimal amount,
                                                   String note,
                                                   boolean includeInAssets,
                                                   boolean selectable,
                                                   Long toAccountId,
                                                   LocalDate date) {
        validateUser(user);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Balance must be positive");
        }
        validateName(name);

        Account toAccount = null;
        if (toAccountId != null) {
            toAccount = accountDAO.findById(toAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("toAccount not found"));
            validateAccountOwnership(toAccount, user);
        }

        LocalDate transactionDate = date != null ? date : LocalDate.now();
        BorrowingAccount borrowingAccount = new BorrowingAccount(
                name,
                amount,
                note,
                includeInAssets,
                selectable,
                user,
                transactionDate);

        String description = toAccount != null
                ? borrowingAccount.getName() + " to " + toAccount.getName()
                : borrowingAccount.getName() + " to External Account";

        Transaction tx = new Transfer(transactionDate,
                description,
                borrowingAccount,
                toAccount,
                amount,
                null);
        transactionDAO.save(tx);
        borrowingAccount.getOutgoingTransactions().add(tx);

        if (toAccount != null) {
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(tx);
            accountDAO.save(toAccount);
        }

        accountDAO.save(borrowingAccount);
        user.getAccounts().add(borrowingAccount);
        userDAO.save(user);

        return borrowingAccount;
    }

    @Transactional
    public LendingAccount createLendingAccount(User user,
                                               String name,
                                               BigDecimal balance,
                                               String note,
                                               boolean includeInAssets,
                                               boolean selectable,
                                               Long fromAccountId,
                                               LocalDate date) {
        validateUser(user);
        validateName(name);

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount = accountDAO.findById(fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("fromAccount not found"));
            validateAccountOwnership(fromAccount, user);
        }

        LocalDate transactionDate = date != null ? date : LocalDate.now();
        LendingAccount lendingAccount = new LendingAccount(name,
                balance,
                note,
                includeInAssets,
                selectable,
                user,
                transactionDate);

        String description = fromAccount != null
                ? fromAccount.getName() + " to " + lendingAccount.getName()
                : "External account to" + lendingAccount.getName();

        Transaction tx = new Transfer(transactionDate,
                description,
                fromAccount,
                lendingAccount,
                balance,
                null);
        transactionDAO.save(tx);
        lendingAccount.getIncomingTransactions().add(tx);

        if (fromAccount != null) {
            fromAccount.debit(balance);
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.save(fromAccount);
        }

        accountDAO.save(lendingAccount);
        user.getAccounts().add(lendingAccount);
        userDAO.save(user);

        return lendingAccount;
    }

    @Transactional
    public void deleteAccount(User user, Long accountId, boolean deleteTransactions) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        List<Transaction> txs=new ArrayList<>(account.getTransactions());
        if (deleteTransactions) {
            /*List<Transaction> txs = transactionDAO.findAll().stream()
                    .filter(tx -> (tx.getFromAccount() != null && tx.getFromAccount().equals(account)) ||
                            (tx.getToAccount() != null && tx.getToAccount().equals(account)))
                    .toList();*/

            for (Transaction tx : txs) {
                if (tx.getFromAccount() != null) {
                    tx.getFromAccount().getOutgoingTransactions().remove(tx);
                    tx.setFromAccount(null);
                }
                if (tx.getToAccount() != null) {
                    tx.getToAccount().getIncomingTransactions().remove(tx);
                    tx.setToAccount(null);
                }
                if (tx.getLedger() != null) {
                    tx.getLedger().getTransactions().remove(tx);
                    tx.setLedger(null);
                }
                if (tx.getCategory() != null) {
                    tx.getCategory().getTransactions().remove(tx);
                    tx.setCategory(null);
                }
                transactionDAO.delete(tx);
            }

        } else {
            /*List<Transaction> txs = transactionDAO.findAll().stream()
                    .filter(tx -> (tx.getFromAccount() != null && tx.getFromAccount().equals(account)) ||
                            (tx.getToAccount() != null && tx.getToAccount().equals(account)))
                    .toList();*/

            for (Transaction tx : txs) {
                if (tx.getFromAccount() != null) tx.setFromAccount(null);
                if (tx.getToAccount() != null) tx.setToAccount(null);
                transactionDAO.save(tx);
            }
        }

        accountDAO.delete(account);
        user.getAccounts().remove(account);
        userDAO.save(user);
    }

    @Transactional
    public void hideAccount(User user, Long accountId) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        validateAccountOwnership(account, user);

        account.hide();
        accountDAO.save(account);
    }

    @Transactional
    public void editBasicAccount(User user,
                                 Long accountId,
                                 String name,
                                 BigDecimal balance,
                                 String notes,
                                 Boolean includedInNetAsset,
                                 Boolean selectable) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (!(account instanceof BasicAccount)) {
            throw new IllegalArgumentException("Account is not a basic account");
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);

        accountDAO.save(account);
    }

    @Transactional
    public void editCreditAccount(User user,
                                  Long accountId,
                                  String name,
                                  BigDecimal balance,
                                  String notes,
                                  Boolean includedInNetAsset,
                                  Boolean selectable,
                                  BigDecimal creditLimit,
                                  BigDecimal currentDebt,
                                  Integer billDate,
                                  Integer dueDate) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (!(account instanceof CreditAccount)) {
            throw new IllegalArgumentException("Account is not a credit account");
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (creditLimit != null) ((CreditAccount) account).setCreditLimit(creditLimit);
        if (currentDebt != null) {
            ((CreditAccount) account).setCurrentDebt(currentDebt);
        }
        if (billDate != null) ((CreditAccount) account).setBillDay(billDate);
        if (dueDate != null) ((CreditAccount) account).setDueDay(dueDate);

        accountDAO.save(account);
    }

    @Transactional
    public void editLoanAccount(User user,
                               Long accountId,
                               String name,
                               String notes,
                               Boolean includedInNetAsset,
                               Integer totalPeriods,
                               Integer repaidPeriods,
                               BigDecimal annualInterestRate,
                               BigDecimal loanAmount,
                               LocalDate repaymentDate,
                               LoanAccount.RepaymentType repaymentType) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (!(account instanceof LoanAccount)) {
            throw new IllegalArgumentException("Account is not a loan account");
        }

        if (name != null) account.setName(name);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (totalPeriods != null) ((LoanAccount) account).setTotalPeriods(totalPeriods);
        if (repaidPeriods != null) ((LoanAccount) account).setRepaidPeriods(repaidPeriods);
        if (annualInterestRate != null) ((LoanAccount) account).setAnnualInterestRate(annualInterestRate);
        if (loanAmount != null) ((LoanAccount) account).setLoanAmount(loanAmount);
        if (repaymentDate != null) ((LoanAccount) account).setRepaymentDate(repaymentDate);
        if (repaymentType != null) ((LoanAccount) account).setRepaymentType(repaymentType);

        ((LoanAccount) account).updateRemainingAmount();

        accountDAO.save(account);
    }
    @Transactional
    public void editBorrowingAccount(User user,
                                     Long accountId,
                                     String name,
                                     BigDecimal balance,
                                     String notes,
                                     Boolean includedInNetAsset,
                                     Boolean selectable) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (!(account instanceof BorrowingAccount)) {
            throw new IllegalArgumentException("Account is not a borrowing account");
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);

        accountDAO.save(account);
    }

    @Transactional
    public void editLendingAccount(User user,
                                  Long accountId,
                                  String name,
                                  BigDecimal balance,
                                  String notes,
                                  Boolean includedInNetAsset,
                                  Boolean selectable) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);
        if (!(account instanceof LendingAccount)) {
            throw new IllegalArgumentException("Account is not a lending account");
        }
        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        accountDAO.save(account);
    }


    @Transactional
    public void creditAccount(User user, Long accountId, BigDecimal amount) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (account instanceof LoanAccount) {
            throw new IllegalArgumentException("Cannot credit a loan account");
        }

        if (!account.getSelectable()) {
            throw new IllegalArgumentException("Cannot credit a non-selectable account");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        account.credit(amount);
        accountDAO.save(account);
    }

    @Transactional
    public void debitAccount(User user, Long accountId, BigDecimal amount) {
        validateUser(user);
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        if (account instanceof LoanAccount) {
            throw new IllegalArgumentException("Cannot debit a loan account");
        }

        if (!account.getSelectable()) {
            throw new IllegalArgumentException("Cannot debit a non-selectable account");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (account instanceof CreditAccount) {
            //handleCreditAccountDebit((CreditAccount) account, amount);
            if (amount.compareTo(account.getBalance()) > 0) {
                BigDecimal deficit = amount.subtract(account.getBalance());
                BigDecimal newDebt = ((CreditAccount) account).getCurrentDebt().add(deficit);

                if (newDebt.compareTo(((CreditAccount) account).getCreditLimit()) > 0) {
                    throw new IllegalArgumentException("Amount exceeds credit limit");
                }

                ((CreditAccount) account).setCurrentDebt(newDebt.setScale(2, RoundingMode.HALF_UP));
                account.setBalance(BigDecimal.ZERO);
            } else {
                account.debit(amount);
            }
        } else {
            if (amount.compareTo(account.getBalance()) > 0) {
                throw new IllegalArgumentException("Insufficient funds");
            }
            account.debit(amount);
        }

        accountDAO.save(account);
    }

    @Transactional
    public void repayDebt(User user,
                          Long creditAccountId,
                          BigDecimal amount,
                          Long fromAccountId,
                          Long ledgerId) {
        validateUser(user);
        Account creditAccount = accountDAO.findById(creditAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Credit account not found"));

        if (!(creditAccount instanceof CreditAccount)) {
            throw new IllegalArgumentException("Account is not a credit account");
        }

        validateAccountOwnership(creditAccount, user);

        Ledger ledger = null;
        if(ledgerId!=null){
            ledger = ledgerDAO.findById(ledgerId)
                    .orElseThrow(() -> new IllegalArgumentException("Ledger not found"));
            if(!ledger.getOwner().getId().equals(user.getId())){
                throw new SecurityException("Ledger does not belong to user");
            }
        }

        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount = accountDAO.findById(fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("fromAccount not found"));
            validateAccountOwnership(fromAccount, user);
        }

        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.compareTo(((CreditAccount) creditAccount).getCurrentDebt()) > 0) {
            throw new IllegalArgumentException("Repayment amount exceeds current debt");
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                creditAccount,
                amount,
                ledger);
        transactionDAO.save(tx);

        ((CreditAccount) creditAccount).repayDebt(tx);
        accountDAO.save(creditAccount);

        if (fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.save(fromAccount);
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
    }

    @Transactional
    public void repayLoan(User user,
                          Long loanAccountId,
                          Long fromAccountId,
                          BigDecimal amount,
                          Long ledgerId) {
        validateUser(user);

        Account loanAccount = accountDAO.findById(loanAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found"));
        if (!(loanAccount instanceof LoanAccount)) {
            throw new IllegalArgumentException("Account is not a loan account");
        }

        validateAccountOwnership(loanAccount, user);

        LoanAccount loanAcc = (LoanAccount) loanAccount;

        if (loanAcc.getRepaidPeriods() >= loanAcc.getTotalPeriods()) {
            throw new IllegalArgumentException("Loan is already fully repaid");
        }

        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount = accountDAO.findById(fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("fromAccount not found"));
            validateAccountOwnership(fromAccount, user);
        }

        BigDecimal repayAmount = amount != null ? amount
                : loanAcc.getMonthlyRepayment(loanAcc.getRepaidPeriods() + 1);

        if (repayAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (repayAmount.compareTo(loanAcc.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Amount exceeds remaining loan amount");
        }

        Ledger ledger = null;
        if(ledgerId != null){
            ledger = ledgerDAO.findById(ledgerId)
                    .orElseThrow(() -> new IllegalArgumentException("Ledger not found"));
            if (!ledger.getOwner().getId().equals(user.getId())) {
                throw new SecurityException("Ledger does not belong to user");
            }
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Loan Repayment",
                fromAccount,
                loanAcc,
                repayAmount,
                ledger);
        transactionDAO.save(tx);

        loanAcc.repayLoan(tx, repayAmount);
        accountDAO.save(loanAcc);

        if (fromAccount != null) {
            fromAccount.debit(repayAmount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.save(fromAccount);
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
    }

    @Transactional
    public void receiveLending(User user,
                                  Long lendingAccountId,
                                  BigDecimal amount,
                                  Long toAccountId,
                                  Long ledgerId) {
        validateUser(user);

        Account lendingAccount = accountDAO.findById(lendingAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Lending account not found"));

        if (!(lendingAccount instanceof LendingAccount)) {
            throw new IllegalArgumentException("Account is not a lending account");
        }

        validateAccountOwnership(lendingAccount, user);

        LendingAccount lendAcc = (LendingAccount) lendingAccount;

        Account toAccount = null;
        if (toAccountId != null) {
            toAccount = accountDAO.findById(toAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("toAccount not found"));
            validateAccountOwnership(toAccount, user);
        }

        if( amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Ledger ledger = null;
        if(ledgerId != null){
            ledger = ledgerDAO.findById(ledgerId)
                    .orElseThrow(() -> new IllegalArgumentException("Ledger not found"));

            if (!ledger.getOwner().getId().equals(user.getId())) {
                throw new SecurityException("Ledger does not belong to user");
            }
        }

        String description;
        if (toAccount != null) {
            description = lendingAccount.getName() + " to " + toAccount.getName();
        } else {
            description = lendingAccount.getName() + " to External account";
        }

        Transaction tx = new Transfer(LocalDate.now(),
                description,
                lendingAccount,
                toAccount,
                amount,
                ledger);
        transactionDAO.save(tx);

        ((LendingAccount) lendingAccount).receiveRepayment(tx, amount);
        accountDAO.save(lendingAccount);

        if (toAccount != null) {
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(tx);
            accountDAO.save(toAccount);
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
    }


    public List<Account> getAllAccounts(User user) {
        validateUser(user);
        return accountDAO.findByOwnerId(user.getId());
    }

    public List<Transaction> getAccountTransactionsForMonth(User user,
                                                            Long accountId,
                                                            YearMonth month) {
        validateUser(user);

        if(accountId==null){
            throw new IllegalArgumentException("Account cannot be null");
        }
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if(month==null){
            throw new IllegalArgumentException("Month cannot be null");
        }


        validateAccountOwnership(account, user);

        return transactionDAO.findByAccountIdAndOwnerId(
                accountId,
                user.getId(),
                month.atDay(1),
                month.atEndOfMonth());
    }

    public Map<String, Object> getMonthlySummary(User user,
                                                 Long accountId,
                                                 YearMonth month) {
        validateUser(user);
        if(accountId==null){
            throw new IllegalArgumentException("Account cannot be null");
        }
        Account account = accountDAO.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateAccountOwnership(account, user);

        BigDecimal totalIncome = account.getIncomingTransactions().stream()
                .filter(tx -> isInMonth(tx.getDate(), month))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = account.getOutgoingTransactions().stream()
                .filter(tx -> isInMonth(tx.getDate(), month))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("month", month.toString());
        summary.put("totalIncome", totalIncome);
        summary.put("totalExpense", totalExpense);

        return summary;
    }

    // metodi privati di validazione e utilità
    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
    }

    private void validateAccountOwnership(Account account, User user) {
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Account does not belong to user");
        }
    }


    private void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
    }


    private boolean isInMonth(LocalDate date, YearMonth month) {
        return date.getYear() == month.getYear() &&
                date.getMonthValue() == month.getMonthValue();
    }
}
