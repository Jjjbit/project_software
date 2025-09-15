package com.ledger.project_software.business;
import com.ledger.project_software.Repository.*;
import com.ledger.project_software.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ledgers")
public class LedgerController {
    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;

    @PostMapping("/create-ledger")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createLedger(@RequestParam String name,
                                               Principal principal) {
        User owner=userRepository.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        Ledger ledger=new Ledger(name, owner);
        ledgerRepository.save(ledger);

        //List<Category> templateCategories = categoryRepository.findAll();
        List<Category> templateCategories = categoryRepository.findByParentIsNull();

        //copia albero di categorycomponent dal database a ledger
        for (Category template : templateCategories) {
            LedgerCategory ledgerCategory= copyCategory(template, ledger);
            ledger.addCategoryComponent(ledgerCategory);
        }
        return ResponseEntity.ok("ledger created successfully");
    }

    private LedgerCategory copyCategory(Category template, Ledger ledger) {
        LedgerCategory copy;

        if(template.getParent() != null) {
            copy = new LedgerCategory();
            copy.setName(template.getName());
            copy.setType(template.getType());
            copy.setLedger(ledger);
            ledger.addCategoryComponent(copy);
        } else {
            copy = new LedgerCategory();
            copy.setName(template.getName());
            copy.setType(template.getType());
            copy.setLedger(ledger);

            for(Category child : template.getChildren()) {
                LedgerCategory childCopy = copyCategory(child, ledger);
                copy.addChild(childCopy);
                childCopy.setParent(copy);
            }
        }

        return copy;
    }

    @DeleteMapping("/{ledgerId}/delete")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteLedger(@PathVariable Long ledgerId,
                                                  Principal principal) {
        Ledger ledger = ledgerRepository.findById(ledgerId).orElse(null);
        if (ledger == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
        }
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User owner=userRepository.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        List<Transaction> transactionsToDelete = new ArrayList<>(ledger.getTransactions());
        for(Transaction tx :transactionsToDelete){
            Account to = tx.getToAccount();
            Account from = tx.getFromAccount();
            LedgerCategory category= tx.getCategory();

            if (tx instanceof Income) {
                if(to != null) {
                    to.debit(tx.getAmount()); //modifica bilancio account
                    to.getIncomingTransactions().remove(tx); //rimuove tx da account
                    tx.setToAccount(null); //rimuove riferimento a account in tx
                    accountRepository.save(to); //salva modifiche a account
                }
            } else if (tx instanceof Expense) {
                if(from != null) {
                    from.credit(tx.getAmount());
                    from.getOutgoingTransactions().remove(tx);
                    tx.setFromAccount(null);
                    accountRepository.save(from);
                }
            } else if (tx instanceof Transfer) {
                if(from != null) {
                    from.credit(tx.getAmount());
                    from.getOutgoingTransactions().remove(tx);
                    tx.setFromAccount(null);
                    accountRepository.save(from);
                }
                if(to != null) {
                    to.debit(tx.getAmount());
                    to.getIncomingTransactions().remove(tx);
                    tx.setToAccount(null);
                    accountRepository.save(to);
                }
            }

            if(category != null) {
                category.getTransactions().remove(tx);
                tx.setCategory(null);
                ledgerCategoryRepository.save(category);
            }

            ledger.getTransactions().remove(tx); //rimuove tx da ledger
            tx.setLedger(null); //rimuove riferimento a ledger in tx
            transactionRepository.delete(tx);
        }

        List<LedgerCategory> categoriesToDelete = new ArrayList<>(ledger.getCategories());
        for (LedgerCategory category: categoriesToDelete ) {
            ledger.getCategories().remove(category);
            category.setLedger(null);
            ledgerCategoryRepository.delete(category);
        }

        owner.getLedgers().remove(ledger); //rimuove ledger da user
        ledgerRepository.delete(ledger);
        return ResponseEntity.ok("delete ledger");
    }
}
