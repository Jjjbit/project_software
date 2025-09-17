package com.ledger.project_software.business;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;
    @Autowired
    private LedgerRepository ledgerRepository;


    @PostMapping("/create-basic-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createBasicAccount(@RequestParam String accountName,
                                                     @RequestParam BigDecimal balance,
                                                     Principal principal,
                                                     @RequestParam (required = false) String note,
                                                     @RequestParam boolean includedInNetWorth,
                                                     @RequestParam boolean selectable,
                                                     @RequestParam AccountType type,
                                                     @RequestParam AccountCategory category) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(category == AccountCategory.CREDIT || category == AccountCategory.VIRTUAL_ACCOUNT){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid account type for basic account");
        }
        BasicAccount account = new BasicAccount(
                accountName,
                balance,
                note,
                includedInNetWorth,
                selectable,
                type,
                category,
                user);
        accountRepository.save(account);
        user.getAccounts().add(account);
        userRepository.save(user);
        return ResponseEntity.ok("Basic account created successfully");
    }

    @PostMapping("/create-credit-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createCreditAccount(@RequestParam String accountName,
                                                      @RequestParam BigDecimal balance,
                                                      Principal principal,
                                                      @RequestParam (required = false) String note,
                                                      @RequestParam boolean includedInNetWorth,
                                                      @RequestParam boolean selectable,
                                                      @RequestParam BigDecimal creditLimit,
                                                      @RequestParam (required = false) BigDecimal currentDebt,
                                                      @RequestParam (required = false) Integer billDate,
                                                      @RequestParam (required = false) Integer dueDate,
                                                      @RequestParam AccountType type) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(type != AccountType.CREDIT_CARD ){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid account type for credit account");
        }
        CreditAccount account = new CreditAccount(
                accountName,
                balance,
                user,
                note,
                includedInNetWorth,
                selectable,
                creditLimit,
                currentDebt,
                billDate,
                dueDate,
                type);
        accountRepository.save(account);
        user.getAccounts().add(account);
        userRepository.save(user);
        return ResponseEntity.ok("Credit account created successfully");
    }

    @PostMapping("/create-loan-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createLoanAccount(@RequestParam String accountName,
                                                    @RequestParam (required = false) String note,
                                                    @RequestParam boolean includedInNetAsset,
                                                    Principal principal,
                                                    @RequestParam int totalPeriods,
                                                    @RequestParam int repaidPeriods,
                                                    @RequestParam BigDecimal annualInterestRate,
                                                    @RequestParam BigDecimal loanAmount,
                                                    @RequestParam (required = false) Long receivingAccountId,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate repaymentDate,
                                                    @RequestParam (required = false) LoanAccount.RepaymentType repaymentType) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account receivingAccount=null;
        if(receivingAccountId != null) {
            receivingAccount = accountRepository.findById(receivingAccountId).orElse(null);
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

        Transaction tx= new Transfer(
                LocalDate.now(),
                "Loan disbursement",
                account,
                receivingAccount,
                loanAmount,
                null);
        account.getOutgoingTransactions().add(tx);
        accountRepository.save(account);
        user.getAccounts().add(account);
        userRepository.save(user);
        return ResponseEntity.ok("Loan account created successfully");
    }

    @PostMapping("/create-borrowing-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createBorrowingAccount(@RequestParam (required = false) Long toAccountId,
                                                         @RequestParam String note,
                                                         @RequestParam BigDecimal amount,
                                                         Principal principal,
                                                         @RequestParam boolean includeInAssets,
                                                         @RequestParam boolean selectable,
                                                         @RequestParam String name,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account toAccount= null;
        if(toAccountId != null) {
            toAccount=accountRepository.findById(toAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "toAccount not found")
                    );
            if(!toAccount.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("toAccount does not belong to the authenticated user");
            }
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Balance cannot be negative");
        }
        BorrowingAccount borrowingAccount = new BorrowingAccount(
                name,
                amount,
                note,
                includeInAssets,
                selectable,
                user,
                date != null ? date : LocalDate.now());

        String description;
        if (toAccount != null) {
            description = borrowingAccount.getName() + " to " + toAccount.getName();
        } else {
            description =  borrowingAccount.getName() + "to " + "External Account ";
        }

        Transaction initialTransaction = new Transfer(
                date != null ? date : LocalDate.now(),
                description,
                borrowingAccount,
                toAccount,
                amount,
                null);
        transactionRepository.save(initialTransaction);

        borrowingAccount.getOutgoingTransactions().add(initialTransaction);

        if(toAccount != null) {
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(initialTransaction);
            accountRepository.save(toAccount);
        }
        accountRepository.save(borrowingAccount);
        user.getAccounts().add(borrowingAccount);
        userRepository.save(user);
        return ResponseEntity.ok("Borrowing added successfully");
    }
    @PostMapping("/create-lending-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createLendingAccount(@RequestParam String name,
                                                       Principal principal,
                                                       @RequestParam BigDecimal balance,
                                                       @RequestParam String note,
                                                       @RequestParam boolean includeInNetWorth,
                                                       @RequestParam boolean selected,
                                                       @RequestParam (required = false) Long fromAccountId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount = accountRepository.findById(fromAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "fromAccount not found"));

            if (!fromAccount.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("fromAccount does not belong to the authenticated user");
            }
        }

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Balance cannot be negative");
        }
        LendingAccount lendingAccount = new LendingAccount(
                name,
                balance,
                note,
                includeInNetWorth,
                selected,
                user,
                date != null ? date : LocalDate.now());

        String description;
        if (fromAccount != null) {
            description = fromAccount.getName() + " to " + lendingAccount.getName();
        } else {
            description = lendingAccount.getName() +"to" + "External account";
        }

        Transaction initialTransaction = new Transfer(
                date != null ? date : LocalDate.now(),
                description,
                fromAccount,
                lendingAccount,
                balance,
                null //ledger
        );


        lendingAccount.getIncomingTransactions().add(initialTransaction);
        accountRepository.save(lendingAccount);
        if(fromAccount != null){
            fromAccount.debit(balance);
            fromAccount.getOutgoingTransactions().add(initialTransaction);
            accountRepository.save(fromAccount);
        }
        transactionRepository.save(initialTransaction);
        user.getAccounts().add(lendingAccount);
        userRepository.save(user);
        return ResponseEntity.ok("Lending added successfully");
    }



    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteAccount(@PathVariable Long id,
                                                @RequestParam boolean deleteTransactions,
                                                Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot delete someone else's account");
        }

        if (deleteTransactions) {// Delete all transactions associated with the account
            List<Transaction> transactionsToDelete =
                    transactionRepository.findAll().stream()
                            .filter(tx -> (tx.getFromAccount() != null && tx.getFromAccount().equals(account)) ||
                                    ( tx.getToAccount() != null && tx.getToAccount().equals(account)))
                            .toList();

            for (Transaction transaction : transactionsToDelete) {

                if (transaction.getFromAccount() != null) {
                    transaction.getFromAccount().getOutgoingTransactions().remove(transaction);
                    transaction.setFromAccount(null);
                }

                if (transaction.getToAccount() != null) {
                    transaction.getToAccount().getIncomingTransactions().remove(transaction);
                    transaction.setToAccount(null);
                }

                if (transaction.getLedger() != null) {
                    transaction.getLedger().getTransactions().remove(transaction);
                    transaction.setLedger(null);
                }

                if (transaction.getCategory() != null){
                    transaction.getCategory().getTransactions().remove(transaction);
                    transaction.setCategory(null);
                }

                transactionRepository.delete(transaction);

            }

            accountRepository.delete(account);
            user.getAccounts().remove(account);
            userRepository.save(user);
            return ResponseEntity.ok("Account and associated transactions deleted successfully");
        } else {
            // If not deleting transactions, just disassociate them
            List<Transaction> transactionsToDisassociate =
                    transactionRepository.findAll().stream()
                            .filter(tx -> (tx.getFromAccount() != null && tx.getFromAccount().equals(account)) ||
                                    ( tx.getToAccount() != null && tx.getToAccount().equals(account)))
                            .toList();
            for (Transaction transaction : transactionsToDisassociate) {
                if(transaction.getFromAccount() != null) {
                    transaction.setFromAccount(null);
                }

                if (transaction.getToAccount() != null) {
                    transaction.setToAccount(null);
                }
                transactionRepository.save(transaction);
            }

            accountRepository.delete(account);
            user.getAccounts().remove(account);
            userRepository.save(user);
            return ResponseEntity.ok("Account disassociated from transactions and deleted successfully");
        }
    }

    @PutMapping("/{id}/hide")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> hideAccount(@PathVariable Long id,
                                              Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot hide someone else's account");
        }

        account.hide();
        accountRepository.save(account);
        return ResponseEntity.ok("Account hidden successfully");
    }

    @PutMapping("/{id}/edit-basic-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editBasicAccount(@PathVariable Long id,
                                                   Principal principal,
                                                   @RequestParam (required = false) String name,
                                                   @RequestParam (required = false) BigDecimal balance,
                                                   @RequestParam (required = false) String notes,
                                                   @RequestParam (required = false) Boolean includedInNetAsset,
                                                   @RequestParam (required = false) Boolean selectable) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot edit someone else's account");
        }

        if (!(account instanceof BasicAccount)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a basic account");
        }

        if (name!=null) {
            account.setName(name);
        }
        if(balance !=null){
            account.setBalance(balance);
        }
        account.setNotes(notes);

        if(includedInNetAsset != null){
            account.setIncludedInNetAsset(includedInNetAsset);
        }

        if(selectable !=null){
            account.setSelectable(selectable);
        }

        accountRepository.save(account);
        userRepository.save(account.getOwner());
        return ResponseEntity.ok("Account edited successfully");
    }

    @PutMapping("/{id}/edit-credit-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editCreditAccount(@PathVariable Long id,
                                                    Principal principal,
                                                    @RequestParam (required = false) String name,
                                                    @RequestParam (required = false) BigDecimal balance,
                                                    @RequestParam (required = false) String notes,
                                                    @RequestParam (required = false) Boolean includedInNetAsset,
                                                    @RequestParam (required = false) Boolean selectable,
                                                    @RequestParam (required = false) BigDecimal creditLimit,
                                                    @RequestParam (required = false) BigDecimal currentDebt,
                                                    @RequestParam (required = false) Integer billDate,
                                                    @RequestParam (required = false) Integer dueDate) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot edit someone else's account");
        }
        if (!(account instanceof CreditAccount)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a credit account");
        }
        CreditAccount creditAccount = (CreditAccount) account;

        if(name != null){
            creditAccount.setName(name);
        }
        if(balance != null){
            creditAccount.setBalance(balance);
        }
        creditAccount.setNotes(notes);
        if(includedInNetAsset != null){
            creditAccount.setIncludedInNetAsset(includedInNetAsset);
        }
        if(selectable != null){
            creditAccount.setSelectable(selectable);
        }
        if(creditLimit != null){
            creditAccount.setCreditLimit(creditLimit);
        }
        if(currentDebt != null){
            creditAccount.setCurrentDebt(currentDebt);
        }
        if(billDate != null){
            creditAccount.setBillDay(billDate);
        }
        if (dueDate != null){
            creditAccount.setDueDay(dueDate);
        }
        accountRepository.save(creditAccount);
        userRepository.save(creditAccount.getOwner());
        return ResponseEntity.ok("Credit account edited successfully");
    }

    @PutMapping("/{id}/edit-loan-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editLoanAccount(@PathVariable Long id,
                                                  Principal principal,
                                                  @RequestParam (required = false) String name,
                                                  @RequestParam (required = false) String notes,
                                                  @RequestParam (required = false) Boolean includedInNetAsset,
                                                  @RequestParam (required = false) Integer totalPeriods,
                                                  @RequestParam (required = false) Integer repaidPeriods,
                                                  @RequestParam (required = false) BigDecimal annualInterestRate,
                                                  @RequestParam (required = false) BigDecimal loanAmount,
                                                  @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate repaymentDate,
                                                  @RequestParam (required = false) LoanAccount.RepaymentType repaymentType) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot edit someone else's account");
        }
        if (!(account instanceof LoanAccount)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a loan account");
        }
        LoanAccount loanAccount = (LoanAccount) account;
        if(name != null){loanAccount.setName(name);}
        loanAccount.setNotes(notes);
        if(includedInNetAsset != null){loanAccount.setIncludedInNetAsset(includedInNetAsset);}
        if(totalPeriods != null){loanAccount.setTotalPeriods(totalPeriods);}
        if(repaidPeriods != null){
            loanAccount.setRepaidPeriods(repaidPeriods);
        }
        if(annualInterestRate != null){loanAccount.setAnnualInterestRate(annualInterestRate);}
        if(loanAmount != null){loanAccount.setLoanAmount(loanAmount);}
        if(repaymentDate != null) {loanAccount.setRepaymentDate(repaymentDate);}
        if(repaymentType != null){loanAccount.setRepaymentType(repaymentType);}
        ((LoanAccount) account).updateRemainingAmount();
        accountRepository.save(loanAccount);
        userRepository.save(loanAccount.getOwner());
        return ResponseEntity.ok("Loan account edited successfully");
    }


    @PutMapping("/{id}/credit")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> creditAccount(@PathVariable Long id,
                                                @RequestParam BigDecimal amount,
                                                Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot credit someone else's account");
        }

        if(account instanceof LoanAccount){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot credit a loan account");
        }

        account.credit(amount);
        accountRepository.save(account);
        return ResponseEntity.ok("credit account");
    }

    @PutMapping("/{id}/debit")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> debitAccount(@PathVariable Long id,
                                               @RequestParam BigDecimal amount,
                                               Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot debit someone else's account");
        }

        if(account instanceof LoanAccount){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot debit a loan account");
        }
        account.debit(amount);
        accountRepository.save(account);
        return ResponseEntity.ok("debit account");
    }

    //CreditAccount
    @PutMapping("{id}/repay-debt")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> repayDebt(@PathVariable Long id,
                                            @RequestParam BigDecimal amount,
                                            @RequestParam(required = false) Long fromAccountId,
                                            Principal principal,
                                            @RequestParam (required = false) Long ledgerId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        CreditAccount creditAccount = (CreditAccount) accountRepository.findById(id).orElse(null);
        if (creditAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Credit account not found");
        }
        Account fromAccount = (fromAccountId != null) ? accountRepository.findById(fromAccountId)
                .orElse(null) : null;

        if(fromAccount != null){
            if(!fromAccount.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot use someone else's account to repay debt");
            }
        }
        if (!creditAccount.getOwner().getId().equals(user.getId()) ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot repay someone else's debt");
        }

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount must be greater than zero");
        }

        Ledger ledger= (ledgerId != null) ? ledgerRepository.findById(ledgerId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found")): null;
        if (!ledger.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the user");
        }

        //creditAccount.repayDebt(amount, fromAccount, ledger);
        Transaction tx = new Transfer(
                LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                creditAccount,
                amount,
                ledger
        );
        creditAccount.repayDebt(tx);
        accountRepository.save(creditAccount);

        if (fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountRepository.save(fromAccount);
        }
        if(ledger != null){
            ledger.getTransactions().add(tx);
            ledgerRepository.save(ledger);
        }
        return ResponseEntity.ok("Debt repaid successfully");
    }

    //CreditAccount
    @PutMapping("{id}/repay-installment-plan")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> repayInstallmentPlan(@PathVariable Long id,
                                                       @RequestParam Long installmentPlanId,
                                                       Principal principal,
                                                       @RequestParam (required = false) BigDecimal amount,
                                                       @RequestParam (required = false) Long ledgerId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        CreditAccount account = (CreditAccount) accountRepository.findById(id).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Credit account not found");
        }
        InstallmentPlan installmentPlan = installmentPlanRepository.findById(installmentPlanId).orElse(null);
        if (installmentPlan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Installment plan not found");
        }

        if (!account.getOwner().equals(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot repay someone else's installment plan");
        }

        if (!account.getInstallmentPlans().contains(installmentPlan)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Installment plan does not belong to this account");
        }
        Ledger ledger=null;
        if(ledgerId != null) {
            ledger= ledgerRepository.findById(ledgerId).orElse(null);
        }

        if(amount != null){
            account.repayInstallmentPlan(installmentPlan, amount, ledger);
        }else {
            account.repayInstallmentPlan(installmentPlan, ledger);
        }
        if(ledger != null){
            ledgerRepository.save(ledger);
        }

        accountRepository.save(account);
        return ResponseEntity.ok("Installment plan repaid successfully");
    }

    //LoanAccount
    @PutMapping("{id}/repay-loan")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> repayLoan(@PathVariable Long id,
                                            @RequestParam(required = false) Long fromAccountId,
                                            Principal principal,
                                            @RequestParam (required = false) BigDecimal amount,
                                            @RequestParam (required = false) Long ledgerId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        LoanAccount loanAccount = (LoanAccount) accountRepository.findById(id).orElse(null);
        if (loanAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Loan account not found");
        }

        Account fromAccount = (fromAccountId != null) ? accountRepository.findById(fromAccountId).orElse(null) : null;

        User owner = userRepository.findByUsername(principal.getName());
        if (owner == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Ledger ledger=null;
        if(ledgerId != null) {
            ledger=ledgerRepository.findById(ledgerId).orElse(null);
        }

        if(amount != null){
            loanAccount.repayLoan(fromAccount, amount, ledger);
        }else{
            loanAccount.repayLoan(fromAccount, ledger);
        }

        accountRepository.save(loanAccount);
        if (fromAccount != null) {
            accountRepository.save(fromAccount);
        }
        if(ledger != null){
            ledgerRepository.save(ledger);
        }
        return ResponseEntity.ok("Loan repaid successfully");
    }



    @GetMapping("{id}/get-transacitons-for-month")
    @PreAuthorize("isAuthenticated()")
    public List<Transaction> getAccountTransactionsForMonth(@PathVariable Long id,
                                                            @RequestParam YearMonth month,
                                                            Principal principal) {
        if(principal == null){
            throw new IllegalArgumentException("Unauthorized access");
        }
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            throw new IllegalArgumentException("Unauthorized access");
        }
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You cannot view transactions of someone else's account");
        }
        return account.getTransactionsForMonth(month);
    }
}
