package com.ledger.project_software.business;

import com.ledger.project_software.orm.*;
import com.ledger.project_software.domain.*;
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

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionDAO transactionDAO;
    private final UserDAO userDAO;
    private final LedgerDAO ledgerDAO;
    private final AccountDAO accountDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;

    public TransactionController(TransactionDAO transactionDAO,
                                 UserDAO userDAO,
                                 LedgerDAO ledgerDAO,
                                 AccountDAO accountDAO,
                                 LedgerCategoryDAO ledgerCategoryDAO) {
        this.transactionDAO = transactionDAO;
        this.userDAO = userDAO;
        this.ledgerDAO = ledgerDAO;
        this.accountDAO = accountDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
    }

    @PostMapping("/create")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createTransaction(@RequestParam Long ledgerId,
                                                    @RequestParam (required = false) Long fromAccountId,
                                                    @RequestParam (required = false) Long toAccountId,
                                                    @RequestParam (required = false) Long categoryId,
                                                    @RequestParam (required = false) String description,
                                                    @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, //ISO 8601 format: "YYYY-MM-DD"
                                                    @RequestParam BigDecimal amount,
                                                    Principal principal,
                                                    @RequestParam TransactionType type) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user= userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Ledger ledger= ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
        if (!ledger.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the user");
        }

        Account fromAccount = null;
        if (fromAccountId != null) {
            fromAccount= accountDAO.findById(fromAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "From Account not found"));
            if (!fromAccount.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("From Account does not belong to the user");
            }
        }

        Account toAccount = null;
        if (toAccountId != null) {
            toAccount= accountDAO.findById(toAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "To Account not found"));
            if (!toAccount.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("To Account does not belong to the user");
            }
        }

        LedgerCategory categoryComponent = null;
        if (categoryId != null) {
            categoryComponent= ledgerCategoryDAO.findById(categoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (!categoryComponent.getLedger().getId().equals(ledger.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Category does not belong to the specified ledger");
            }
        }

        if(fromAccount == null && toAccount == null){
            return ResponseEntity.badRequest().body("At least one of fromAccountId or toAccountId must be provided");
        }

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            return ResponseEntity.badRequest().body("Amount must be greater than zero");
        }
        if(fromAccount != null){
            if(!fromAccount.getSelectable()){
                return ResponseEntity.badRequest().body("fromAccount is not selectable");
            }
            if(fromAccount instanceof LoanAccount){
                return ResponseEntity.badRequest().body("fromAccount cannot be a LoanAccount");
            }
            if(fromAccount.getBalance().compareTo(amount) < 0){
                return ResponseEntity.badRequest().body("Insufficient funds in fromAccount");
            }
        }
        if(toAccount != null){
            if(!toAccount.getSelectable()){
                return ResponseEntity.badRequest().body("toAccount is not selectable");
            }
            if(toAccount instanceof LoanAccount){
                return ResponseEntity.badRequest().body("toAccount cannot be a LoanAccount");
            }
        }

        if(type == null){
            return ResponseEntity.badRequest().body("Transaction type must be specified");
        }

        if(type == TransactionType.EXPENSE){
            if(fromAccount == null){
                return ResponseEntity.badRequest().body("Expense transaction must have fromAccount");
            }
            if(categoryComponent == null){
                return ResponseEntity.badRequest().body("Expense transaction must have a category");
            }
            if( categoryComponent.getType() != CategoryType.EXPENSE){
                return ResponseEntity.badRequest().body("Expense transaction must have an Expense category");
            }
            Transaction expenseTransaction = new Expense(
                    date != null ? date : LocalDate.now(),
                    amount,
                    description,
                    fromAccount,
                    ledger,
                    categoryComponent
            );
            transactionDAO.save(expenseTransaction);
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(expenseTransaction);
            ledger.getTransactions().add(expenseTransaction);
            categoryComponent.getTransactions().add(expenseTransaction);
            accountDAO.save(fromAccount);
            ledgerCategoryDAO.save(categoryComponent);
            ledgerDAO.save(ledger);
        } else if(type == TransactionType.INCOME){
            if(toAccount == null){
                return ResponseEntity.badRequest().body("Income transaction must have toAccount");
            }
            if(categoryComponent == null){
                return ResponseEntity.badRequest().body("Income transaction must have a category");
            }
            if(categoryComponent.getType() != CategoryType.INCOME){
                return ResponseEntity.badRequest().body("Income transaction must have an Income category");
            }
            Transaction incomeTransaction = new Income(
                    date != null ? date : LocalDate.now(),
                    amount,
                    description,
                    toAccount,
                    ledger,
                    categoryComponent
            );
            transactionDAO.save(incomeTransaction);
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(incomeTransaction);
            ledger.getTransactions().add(incomeTransaction);
            categoryComponent.getTransactions().add(incomeTransaction);
            accountDAO.save(toAccount);
            ledgerCategoryDAO.save(categoryComponent);
            ledgerDAO.save(ledger);
        } else if(type == TransactionType.TRANSFER){
            if(fromAccount != null && toAccount != null && fromAccount.getId().equals(toAccount.getId())){
                return ResponseEntity.badRequest().body("fromAccount and toAccount cannot be the same");
            }
            Transaction transferTransaction = new Transfer(
                    date != null ? date : LocalDate.now(),
                    description,
                    fromAccount,
                    toAccount,
                    amount,
                    ledger
            );
            transactionDAO.save(transferTransaction);
            if(fromAccount != null){
                fromAccount.debit(amount);
                fromAccount.getOutgoingTransactions().add(transferTransaction);
                accountDAO.save(fromAccount);
            }
            if(toAccount != null){
                toAccount.credit(amount);
                toAccount.getIncomingTransactions().add(transferTransaction);
                accountDAO.save(toAccount);
            }
            ledger.getTransactions().add(transferTransaction);
            ledgerDAO.save(ledger);

        } else {
            return ResponseEntity.badRequest().body("Invalid transaction type");
        }

        return ResponseEntity.ok("Transaction created successfully");
    }

    @DeleteMapping("{id}/delete")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteTransaction (@PathVariable Long id,
                                                     Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Transaction transaction = transactionDAO.findById(id).orElse(null);
        if(transaction == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Transaction not found");
        }

        Ledger ledger = transaction.getLedger();
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();
        LedgerCategory categoryComponent = transaction.getCategory();

        if(ledger != null){
            ledger.getTransactions().remove(transaction);
            transaction.setLedger(null);
            ledgerDAO.save(ledger);
        }
        if(fromAccount != null){
            fromAccount.getOutgoingTransactions().remove(transaction);
            fromAccount.credit(transaction.getAmount());
            transaction.setFromAccount(null);
            accountDAO.save(fromAccount);
        }
        if(toAccount != null){
            toAccount.getIncomingTransactions().remove(transaction);
            toAccount.debit(transaction.getAmount());
            transaction.setToAccount(null);
            accountDAO.save(toAccount);
        }
        if(categoryComponent != null){
            categoryComponent.getTransactions().remove(transaction);
            transaction.setCategory(null);
            ledgerCategoryDAO.save(categoryComponent);
        }

        transactionDAO.delete(transaction);
        return ResponseEntity.ok("Transaction deleted successfully");
    }

    @PutMapping("/{id}/edit")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editTransaction(@PathVariable Long id,
                                                  Principal principal,
                                                  @RequestParam (required = false) Long fromAccountId,
                                                  @RequestParam (required = false) Long toAccountId,
                                                  @RequestParam (required = false) Long categoryId,
                                                  @RequestParam (required = false) String note,
                                                  @RequestParam (required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, //ISO 8601 format: "YYYY-MM-DD"
                                                  @RequestParam (required = false) BigDecimal amount,
                                                  @RequestParam (required = false) Long ledgerId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        Transaction transaction = transactionDAO.findById(id).orElse(null);
        if(transaction == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Transaction not found");
        }

        Ledger oldLedger = transaction.getLedger();
        if(ledgerId != null){
            Ledger ledger= ledgerDAO.findById(ledgerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));

            if (!ledger.getOwner().getId().equals(owner.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ledger does not belong to the user");
            }

            if(oldLedger != null && !oldLedger.getId().equals(ledger.getId())){
                oldLedger.getTransactions().remove(transaction);
                ledger.getTransactions().add(transaction);
                transaction.setLedger(ledger);
                ledgerDAO.save(oldLedger);
                ledgerDAO.save(ledger);
            } else if (oldLedger == null) {
                ledger.getTransactions().add(transaction);
                transaction.setLedger(ledger);
                ledgerDAO.save(ledger);
            }
        }

        LedgerCategory oldCategory = transaction.getCategory();
        if (categoryId != null) {
            LedgerCategory categoryComponent= ledgerCategoryDAO.findById(categoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (oldLedger != null && !categoryComponent.getLedger().getId().equals(oldLedger.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Category does not belong to the specified ledger");
            }
            if(transaction instanceof Income && categoryComponent.getType() != CategoryType.INCOME){
                return ResponseEntity.badRequest().body("Income transaction must have an Income category");
            }
            if(transaction instanceof Expense && categoryComponent.getType() != CategoryType.EXPENSE){
                return ResponseEntity.badRequest().body("Expense transaction must have an Expense category");
            }
            if(oldCategory != null && !categoryComponent.getId().equals(oldCategory.getId())){
                oldCategory.getTransactions().remove(transaction);
                categoryComponent.getTransactions().add(transaction);
                transaction.setCategory(categoryComponent);
                ledgerCategoryDAO.save(oldCategory);
                ledgerCategoryDAO.save(categoryComponent);
            } else if (oldCategory == null) {
                categoryComponent.getTransactions().add(transaction);
                transaction.setCategory(categoryComponent);
                ledgerCategoryDAO.save(categoryComponent);
            }
        }


        if(transaction instanceof Expense){
            if(toAccountId != null ){
                return ResponseEntity.badRequest().body("Expense transaction cannot have toAccount");
            }

        }
        if(transaction instanceof Income){
            if(fromAccountId != null){
                return ResponseEntity.badRequest().body("Income transaction cannot have fromAccount");
            }
        }
        if(transaction instanceof Transfer){
            if(fromAccountId != null && toAccountId != null) {
                if (fromAccountId.equals(toAccountId)) {
                    return ResponseEntity.badRequest().body("fromAccount and toAccount cannot be the same");
                }
            }
        }

        if(amount != null) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body("Amount must be greater than zero");
            }
        }else {
            amount = transaction.getAmount();
        }
        Account prevFromAccount = transaction.getFromAccount();
        Account prevToAccount = transaction.getToAccount();
        if(amount.compareTo(transaction.getAmount()) != 0){ //change amount
            if(fromAccountId == null || fromAccountId.equals(prevFromAccount.getId())){ //fromAccount not changed or not provided
                if(prevFromAccount !=null) {
                    prevFromAccount.credit(transaction.getAmount()); //rollback previous amount
                    if (prevFromAccount.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                        return ResponseEntity.badRequest().body("Insufficient funds in fromAccount");
                    }
                    prevFromAccount.debit(amount);
                    accountDAO.save(prevFromAccount);
                }
            }else{ //fromAccount changed
                Account fromAccount= accountDAO.findById(fromAccountId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "From Account not found"));
                if (!fromAccount.getOwner().getId().equals(owner.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("From Account does not belong to the user");
                }
                if(!fromAccount.getSelectable()){
                    return ResponseEntity.badRequest().body("fromAccount is not selectable");
                }
                if(fromAccount instanceof LoanAccount){
                    return ResponseEntity.badRequest().body("fromAccount cannot be a LoanAccount");
                }


                prevFromAccount.credit(transaction.getAmount());
                prevFromAccount.getOutgoingTransactions().remove(transaction);
                accountDAO.save(prevFromAccount);

                if(fromAccount.getBalance().compareTo(amount) < 0){
                    return ResponseEntity.badRequest().body("Insufficient funds in fromAccount");
                }
                fromAccount.debit(amount);
                fromAccount.getOutgoingTransactions().add(transaction);
                transaction.setFromAccount(fromAccount);
                accountDAO.save(fromAccount);
            }

            if(toAccountId == null || toAccountId.equals(prevToAccount.getId())){ //toAccount not changed
                if(prevToAccount !=null) {
                    prevToAccount.debit(transaction.getAmount());
                    prevToAccount.credit(amount);
                    accountDAO.save(prevToAccount);
                }
            }else{ //toAccount changed
                Account toAccount= accountDAO.findById(toAccountId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "To Account not found"));
                if (!toAccount.getOwner().getId().equals(owner.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("To Account does not belong to the user");
                }
                if(!toAccount.getSelectable()){
                    return ResponseEntity.badRequest().body("toAccount is not selectable");
                }


                prevToAccount.debit(transaction.getAmount());
                prevToAccount.getIncomingTransactions().remove(transaction);
                accountDAO.save(prevToAccount);

                toAccount.credit(amount);
                toAccount.getIncomingTransactions().add(transaction);
                transaction.setToAccount(toAccount);
                accountDAO.save(toAccount);
            }
            transaction.setAmount(amount);
        }else{ //amount not changed
            //account changed
            if((fromAccountId != null && (prevFromAccount == null || !fromAccountId.equals(prevFromAccount.getId()))) ||
               (toAccountId != null && (prevToAccount == null || !toAccountId.equals(prevToAccount.getId())))){

                //rollback previous transaction
                if(prevFromAccount != null){
                    prevFromAccount.credit(transaction.getAmount());
                    prevFromAccount.getOutgoingTransactions().remove(transaction);
                    accountDAO.save(prevFromAccount);
                }
                if(prevToAccount != null){
                    prevToAccount.debit(transaction.getAmount());
                    prevToAccount.getIncomingTransactions().remove(transaction);
                    accountDAO.save(prevToAccount);
                }

                //apply new transaction
                if(fromAccountId != null){
                    Account fromAccount= accountDAO.findById(fromAccountId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "From Account not found"));
                    if (!fromAccount.getOwner().getId().equals(owner.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("From Account does not belong to the user");
                    }
                    if(!fromAccount.getSelectable()){
                        return ResponseEntity.badRequest().body("fromAccount is not selectable");
                    }
                    if(fromAccount.getBalance().compareTo(amount) < 0){
                        return ResponseEntity.badRequest().body("Insufficient funds in fromAccount");
                    }
                    fromAccount.debit(amount);
                    fromAccount.getOutgoingTransactions().add(transaction);
                    transaction.setFromAccount(fromAccount);
                    accountDAO.save(fromAccount);
                } else {
                    transaction.setFromAccount(null);
                }
                if(toAccountId != null){
                    Account toAccount= accountDAO.findById(toAccountId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "To Account not found"));
                    if (!toAccount.getOwner().getId().equals(owner.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("To Account does not belong to the user");
                    }
                    if(!toAccount.getSelectable()){
                        return ResponseEntity.badRequest().body("toAccount is not selectable");
                    }
                    if(toAccount instanceof LoanAccount){
                        return ResponseEntity.badRequest().body("toAccount cannot be a LoanAccount");
                    }
                    toAccount.credit(amount);
                    toAccount.getIncomingTransactions().add(transaction);
                    transaction.setToAccount(toAccount);
                    accountDAO.save(toAccount);
                } else {
                    transaction.setToAccount(null);
                }
            }
        }

        transaction.setDate(date != null ? date : transaction.getDate());
        transaction.setNote(note != null ? note : transaction.getNote());

        transactionDAO.save(transaction);

        return ResponseEntity.ok("Edited successfully");

    }
}
