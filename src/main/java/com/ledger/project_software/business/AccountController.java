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
import java.math.RoundingMode;
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

        if(name== null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name cannot be null");
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
        return ResponseEntity.ok("Borrowing account created successfully");
    }

    @PostMapping("/create-lending-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createLendingAccount(@RequestParam String name,
                                                       Principal principal,
                                                       @RequestParam BigDecimal balance,
                                                       @RequestParam String note,
                                                       @RequestParam boolean includeInAssets,
                                                       @RequestParam boolean selectable,
                                                       @RequestParam (required = false) Long fromAccountId,
                                                       @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
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

        if(name== null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name cannot be null");
        }

        LendingAccount lendingAccount = new LendingAccount(
                name,
                balance,
                note,
                includeInAssets,
                selectable,
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
        transactionRepository.save(initialTransaction);

        lendingAccount.getIncomingTransactions().add(initialTransaction);
        accountRepository.save(lendingAccount);

        if(fromAccount != null){
            fromAccount.debit(balance);
            fromAccount.getOutgoingTransactions().add(initialTransaction);
            accountRepository.save(fromAccount);
        }

        user.getAccounts().add(lendingAccount);
        userRepository.save(user);
        return ResponseEntity.ok("Lending account created successfully");
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

    @PutMapping("/{id}/edit-borrowing-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editBorrowingAccount(@PathVariable Long id,
                                                       Principal principal,
                                                       @RequestParam (required = false) String name,
                                                       @RequestParam (required = false) BigDecimal balance,
                                                       @RequestParam (required = false) String notes,
                                                       @RequestParam (required = false) Boolean includedInNetAsset,
                                                       @RequestParam (required = false) Boolean selectable,
                                                       @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null ) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Borrowing account not found");
        }
        if(!account.getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Borrowing account does not belong to the authenticated user");
        }
        if( !(account instanceof BorrowingAccount) ) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a borrowing account");
        }
        if( balance != null ) {
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Balance must be non-negative");
            }
            account.setBalance(balance);
        }
        if(name != null) {
            account.setName(name);
        }
        if(notes != null) {
            account.setNotes(notes);
        }
        if(includedInNetAsset != null) {
            account.setIncludedInNetAsset(includedInNetAsset);
        }
        if(selectable != null) {
            account.setSelectable(selectable);
        }
        if(date != null) {
            ((BorrowingAccount) account).setBorrowingDate(date);
        }
        ((BorrowingAccount) account).checkAndUpdateStatus(); // aggiorna lo stato del borrowing
        accountRepository.save(account);
        return ResponseEntity.ok("Borrowing account updated successfully");
    }

    @PutMapping("/{id}/edit-lending-account")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editLendingAccount(@PathVariable Long id,
                                                    Principal principal,
                                                    @RequestParam (required = false) String name,
                                                    @RequestParam (required = false) BigDecimal balance,
                                                    @RequestParam (required = false) String note,
                                                    @RequestParam (required = false) Boolean includedInNetAsset,
                                                    @RequestParam (required = false) Boolean selectable,
                                                    @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account account = accountRepository.findById(id).orElse(null);
        if(account == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("LendingAccount not found");
        }
        if(!account.getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("LendingAccount does not belong to the user");
        }
        if(!( account instanceof LendingAccount)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a LendingAccount");
        }
        if( balance != null ) {
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Balance must be non-negative");
            }
            account.setBalance(balance);
        }
        if(name != null){account.setName(name);}
        if(note != null) {account.setNotes(note);}
        if(includedInNetAsset != null) {account.setIncludedInNetAsset(includedInNetAsset);}
        if(selectable != null) {account.setSelectable(selectable);}
        if(date != null) {((LendingAccount) account).setLendingDate(date);}
        ((LendingAccount) account).checkAndUpdateStatus();
        accountRepository.save(account);
        return ResponseEntity.ok("LendingAccount updated successfully");
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

        if( account instanceof LoanAccount){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot credit a loan account");
        }
        if(account.getSelectable() == false){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot credit a non-selectable account");
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount must be greater than zero");
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
        if(account.getSelectable() == false){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot debit a non-selectable account");
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount must be greater than zero");
        }
        if( account instanceof CreditAccount){
            if (amount.compareTo(account.getBalance()) > 0) { //amount>balance
                if (((CreditAccount) account).getCurrentDebt().add(amount.subtract(account.getBalance())).compareTo(((CreditAccount) account).getCreditLimit()) > 0) { //currentDebt+(amount-balance)>creditLimit
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount exceeds credit limit");
                } else {
                    ((CreditAccount) account).setCurrentDebt(((CreditAccount) account).getCurrentDebt().add(amount.subtract(account.getBalance())).setScale(2, RoundingMode.HALF_UP));
                    account.setBalance(BigDecimal.ZERO);
                    accountRepository.save(account);
                    return ResponseEntity.ok("debit account");
                }
            }
        }else{
            if (amount.compareTo(account.getBalance()) > 0) { //amount>balance
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient funds");
            }
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

        Account creditAccount = accountRepository.findById(id).orElse(null);
        if (creditAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Credit account not found");
        }
        if( !(creditAccount instanceof CreditAccount) ){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a credit account");
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

        Ledger ledger=null;
        if(ledgerId != null) {
            ledger=ledgerRepository.findById(ledgerId).orElse(null);
            if(ledger == null){
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
            }
            if(!ledger.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the authenticated user");
            }
        }

        Transaction tx = new Transfer(
                LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                creditAccount,
                amount,
                ledger
        );
        transactionRepository.save(tx);
        ((CreditAccount) creditAccount).repayDebt(tx); // aggiorna currentDebt e aggiunge la transazione
        accountRepository.save(creditAccount);

        if (fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountRepository.save(fromAccount);
        }
        if(ledger != null){
            ledger.getTransactions().add(tx);
        }
        return ResponseEntity.ok("Debt repaid successfully");
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

        Account loanAccount = accountRepository.findById(id).orElse(null);
        if (loanAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Loan account not found");
        }
        if( !(loanAccount instanceof LoanAccount) ){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account is not a loan account");
        }

        Account fromAccount = (fromAccountId != null) ? accountRepository.findById(fromAccountId).orElse(null) : null;

        User owner = userRepository.findByUsername(principal.getName());
        if (owner == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        if (!loanAccount.getOwner().getId().equals(owner.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot repay someone else's loan");
        }

        Ledger ledger=null;
        if(ledgerId != null) {
            ledger=ledgerRepository.findById(ledgerId).orElse(null);
            if(ledger == null){
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
            }
            if(!ledger.getOwner().getId().equals(owner.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the authenticated user");
            }
        }

        LoanAccount loanAcc = (LoanAccount) loanAccount;

        if(loanAcc.getRepaidPeriods() >= loanAcc.getTotalPeriods()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Loan is already fully repaid");
        }

        if(amount==null){
            amount=loanAcc.getMonthlyRepayment(loanAcc.getRepaidPeriods() +1);
        }else{
            if(amount.compareTo(BigDecimal.ZERO) <= 0){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount must be greater than zero");
            }
            if(amount.compareTo(loanAcc.getRemainingAmount()) > 0){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Amount exceeds remaining loan amount");
            }
        }

        Transaction repaymentTransaction = new Transfer(
                LocalDate.now(),
                "Loan Repayment",
                fromAccount,
                loanAcc,
                amount,
                ledger
        );
        transactionRepository.save(repaymentTransaction);

        if(amount != null){
            loanAcc.repayLoan(repaymentTransaction, amount); //aggiorna remainingAmount e repaidPeriods
        }else{
            loanAcc.repayLoan(repaymentTransaction); //aggiorna remainingAmount e repaidPeriods
        }
        accountRepository.save(loanAcc);

        if (fromAccount != null) {
            if(!fromAccount.getOwner().getId().equals(owner.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot use someone else's account to repay the loan");
            }
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(repaymentTransaction);
            accountRepository.save(fromAccount);
        }

        if(ledger != null){
            ledger.getTransactions().add(repaymentTransaction);
        }

        return ResponseEntity.ok("Loan repaid successfully");
    }

    //BorrowingAccount
    @PutMapping("/{id}/repay-borrowing")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> repayBorrowing(@PathVariable Long id,
                                                 @RequestParam (required = false) Long fromAccountId,
                                                 @RequestParam BigDecimal amount,
                                                 @RequestParam (required = false) Long ledgerId,
                                                 Principal principal) {
        if(principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account borrowingAccount = accountRepository.findById(id).orElse(null);
        if (borrowingAccount == null ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Borrowing account not found");
        }
        if( !(borrowingAccount instanceof BorrowingAccount) ) {
            return ResponseEntity.badRequest().body("Account is not a borrowing account");
        }
        if(!borrowingAccount.getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Borrowing account does not belong to the authenticated user");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Amount must be positive");
        }
        if(borrowingAccount.getBalance().compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.badRequest().body("Borrowing account is already closed");
        }
        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount = accountRepository.findById(fromAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "fromAccount not found"));

            if (!fromAccount.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("fromAccount does not belong to the authenticated user");
            }
        }

        Ledger ledger = null;
        if(ledgerId != null){
            ledger=ledgerRepository.findById(ledgerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
            if(!ledger.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the authenticated user");
            }
        }

        String description;
        if(fromAccount != null) {
            description = fromAccount.getName() + "to" + borrowingAccount.getName();
        } else {
            description = "External account to " + borrowingAccount.getName();
        }

        Transaction tx = new Transfer(
                LocalDate.now(),
                description,
                fromAccount,
                borrowingAccount,
                amount,
                ledger
        );
        transactionRepository.save(tx);
        ((BorrowingAccount) borrowingAccount).repay(tx, amount); //aggiorna il balance del borrowingAccount e aggiunge la transazione
        accountRepository.save(borrowingAccount);

        if(fromAccount != null){
            fromAccount.debit(amount); //decrementa il balance dell'account
            fromAccount.getOutgoingTransactions().add(tx);
            accountRepository.save(fromAccount);
        }
        if(ledger != null){
            ledger.getTransactions().add(tx);
        }
        return ResponseEntity.ok("Repayment successful");
    }

    //LendingAccount
    @PutMapping("/{id}/receive-lending")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> receiveLending(@PathVariable Long id,
                                                   Principal principal,
                                                   @RequestParam BigDecimal amount,
                                                   @RequestParam (required = false) Long toAccountId,
                                                   @RequestParam (required = false) Long ledgerId){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user=userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Account lendingAccount = accountRepository.findById(id).orElse(null);
        if(lendingAccount == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("LendingAccount not found");
        }
        if(!lendingAccount.getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("LendingAccount does not belong to the user");
        }
        if(!( lendingAccount instanceof LendingAccount)){
            return ResponseEntity.badRequest().body("Account is not a LendingAccount");
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.badRequest().body("Amount must be positive");
        }
        Ledger ledger = null;
        if(ledgerId != null){
            ledger=ledgerRepository.findById(ledgerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
            if(!ledger.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the authenticated user");
            }
        }

        Account toAccount = null;
        if(toAccountId != null){
            toAccount = accountRepository.findById(toAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "toAccount not found"));

            if(!toAccount.getOwner().getId().equals(user.getId())){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("toAccount does not belong to the authenticated user");
            }
        }

        String description;
        if (toAccount != null) {
            description = lendingAccount.getName() + " to " + toAccount.getName();
        } else {
            description = lendingAccount.getName() + " to External account";
        }
        Transaction tx = new Transfer(
                LocalDate.now(),
                description,
                lendingAccount,
                toAccount,
                amount,
                ledger
        );
        transactionRepository.save(tx);
        ((LendingAccount) lendingAccount).receiveRepayment(tx, amount); //aggiorna il balance del lendingAccount e aggiunge la transazione
        accountRepository.save(lendingAccount);

        if(toAccount != null){
            toAccount.credit(amount); //incrementa il balance dell'account
            toAccount.getIncomingTransactions().add(tx);
            accountRepository.save(toAccount);
        }
        if(ledger != null){
            ledger.getTransactions().add(tx);
        }
        return ResponseEntity.ok("Lending received successfully");
    }

    @GetMapping("all-accounts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List <Account>> getAllAccounts(Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Account> accounts = accountRepository.findByOwner(user);
        return ResponseEntity.ok(accounts);
    }



    @GetMapping("{id}/get-transactions-for-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Transaction>> getAccountTransactionsForMonth(@PathVariable Long id,
                                                                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                                            Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!account.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Transaction> transactions = transactionRepository.findByAccountIdAndOwnerId(
                id,
                user.getId(),
                month.atDay(1),
                month.atEndOfMonth());

        return ResponseEntity.ok(transactions);
    }
}
