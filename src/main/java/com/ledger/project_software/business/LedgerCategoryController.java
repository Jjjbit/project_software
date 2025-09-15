package com.ledger.project_software.business;

import com.ledger.project_software.Repository.*;
import com.ledger.project_software.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ledger-categories")
public class LedgerCategoryController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerCategoryRepository ledgerCategoryRepository;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @PostMapping("/create-category")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createCategory(@RequestParam String name,
                                                 @RequestParam(required = false) Long parentId,
                                                 Principal principal,
                                                 @RequestParam Long ledgerId,
                                                 @RequestParam CategoryType type) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        LedgerCategory parent = null;
        if (parentId != null) {
            parent = ledgerCategoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
            if (parent.getParent() != null) {
                return ResponseEntity.badRequest().body("Parent must be a Category");
            }
        }

        Ledger ledger=ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));

        LedgerCategory newCategory = new LedgerCategory(name, type, ledger);
        if (parent != null) {
            newCategory.setParent(parent);
            parent.addChild(newCategory);
            ledgerCategoryRepository.save(parent);
        }
        ledgerCategoryRepository.save(newCategory);
        return ResponseEntity.ok("Category created successfully");
    }
    @PostMapping("/{parentId}/create-subcategory")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createSubCategory(@PathVariable Long parentId,
                                                    @RequestParam String name,
                                                    Principal principal,
                                                    @RequestParam Long ledgerId,
                                                    @RequestParam CategoryType type) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory parent = ledgerCategoryRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
        if (parent.getParent() != null) {
            return ResponseEntity.badRequest().body("Parent must be a Category");
        }
        Ledger ledger=ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
        LedgerCategory newSubCategory = new LedgerCategory(name, type, ledger);
        newSubCategory.setParent(parent);
        parent.addChild(newSubCategory);
        ledgerCategoryRepository.save(parent);
        ledgerCategoryRepository.save(newSubCategory);
        return ResponseEntity.ok("SubCategory created successfully");
    }

    @PutMapping("/{id}/demote")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> demoteCategoryToSubCategory (@PathVariable Long id,
                                                               Principal principal,
                                                               @RequestParam Long parentId){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        LedgerCategory category=ledgerCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (category.getParent() != null) {
            return ResponseEntity.badRequest().body("Must be a Category");
        }

        if (parentId == null || parentId.equals(id)) {
            return ResponseEntity.badRequest().body("Demote must have parentId");
        }

        LedgerCategory parent = ledgerCategoryRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));

        if(parent.getParent() != null){
            return ResponseEntity.badRequest().body("parent must be Category");
        }

        if (!category.getChildren().isEmpty()) {
            return ResponseEntity.badRequest().body("Cannot demote category with subcategories");
        }
        category.setParent(parent);
        parent.addChild(category);

        ledgerCategoryRepository.save(category);
        ledgerCategoryRepository.save(parent);

        return ResponseEntity.ok("Demoted successfully");
    }
    @PutMapping("/{id}/promote")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> promoteSubCategoryToCategory (@PathVariable Long id,
                                                                Principal principal){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category=ledgerCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        LedgerCategory parent=category.getParent();
        if(parent == null){
            return ResponseEntity.badRequest().body("Must be a SubCategory");
        }

        parent.removeChild(category);
        category.setParent(null);
        ledgerCategoryRepository.save(category);
        ledgerCategoryRepository.save(parent);
        return ResponseEntity.ok("Promoted successfully");
    }

    @DeleteMapping("/{id}/delete")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id,
                                                    Principal principal,
                                                 @RequestParam boolean deleteTransactions,
                                                 @RequestParam (required = false) Long migrateToCategoryId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category = ledgerCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getChildren().isEmpty() && category.getParent() == null) {
            return ResponseEntity.badRequest().body("Cannot delete category with subcategories");
        }


        List<Budget> budgetsToDelete = new ArrayList<>(category.getBudgets());
        for (Budget b : budgetsToDelete) {
            budgetRepository.delete(b);
        }

        if (!deleteTransactions) {
            if(migrateToCategoryId == null) {
                return ResponseEntity.badRequest().body("Must provide migrateToCategoryId");
            }
            LedgerCategory migrateToCategory = ledgerCategoryRepository.findById(migrateToCategoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "migrateToCategory not found"));
            if (migrateToCategory.getParent() != null) {
                return ResponseEntity.badRequest().body("migrateToCategory must be a Category");
            }
            List<Transaction> transactionsToMigrate = new ArrayList<>(category.getTransactions());
            for (Transaction tx : transactionsToMigrate) {
                category.getTransactions().remove(tx);
                tx.setCategory(migrateToCategory);
                migrateToCategory.addTransaction(tx);
                transactionRepository.save(tx);
            }
            ledgerCategoryRepository.save(migrateToCategory);
        }else{
            List<Transaction> transactionsToDelete = new ArrayList<>(category.getTransactions());
            for (Transaction tx : transactionsToDelete) {
                tx.setCategory(null);
                category.getTransactions().remove(tx);
                if(tx.getFromAccount() != null){
                    Account from = tx.getFromAccount();
                    from.getOutgoingTransactions().remove(tx);
                    from.credit(tx.getAmount());
                    tx.setFromAccount(null);
                }
                if(tx.getToAccount() != null){
                    Account to = tx.getToAccount();
                    to.getIncomingTransactions().remove(tx);
                    to.debit(tx.getAmount());
                    tx.setToAccount(null);
                }
                if(tx.getLedger() != null){
                    Ledger ledger = tx.getLedger();
                    ledger.getTransactions().remove(tx);
                    tx.setLedger(null);
                }

                transactionRepository.delete(tx);
            }
        }
        if(category.getParent() != null){
            LedgerCategory parent=category.getParent();
            parent.removeChild(category);
            category.setParent(null);
            ledgerCategoryRepository.save(parent);
        }

        Ledger ledger=category.getLedger();
        ledger.getCategories().remove(category);
        category.setLedger(null);
        ledgerRepository.save(ledger);

        ledgerCategoryRepository.delete(category);
        return ResponseEntity.ok("Category deleted successfully");
    }

    @PutMapping("/{id}/rename")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> renameCategory(@PathVariable Long id,
                                                 @RequestParam String newName,
                                                 Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category = ledgerCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        category.setName(newName);
        ledgerCategoryRepository.save(category);
        return ResponseEntity.ok("Category renamed successfully");
    }

    @PutMapping("/{id}/change-parent")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> changeParentCategory(@PathVariable Long id,
                                                       @RequestParam Long newParentId,
                                                       Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(id.equals(newParentId)){
            return ResponseEntity.badRequest().body("Category cannot be its own parent");
        }
        LedgerCategory category = ledgerCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        LedgerCategory newParent = ledgerCategoryRepository.findById(newParentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "New parent category not found"));

        if (newParent.getParent() != null) {
            return ResponseEntity.badRequest().body("New parent must be a Category");
        }
        if(category.getParent() == null){
            return ResponseEntity.badRequest().body("Category must be a SubCategory");
        }

        LedgerCategory oldParent = category.getParent();
        oldParent.removeChild(category);
        category.setParent(newParent);
        newParent.addChild(category);

        ledgerCategoryRepository.save(oldParent);
        ledgerCategoryRepository.save(newParent);
        ledgerCategoryRepository.save(category);
        return ResponseEntity.ok("Parent category changed successfully");
    }

}
