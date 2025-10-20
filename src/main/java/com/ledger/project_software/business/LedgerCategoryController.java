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

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ledger-categories")
public class LedgerCategoryController {
    private final UserDAO userDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final LedgerDAO ledgerDAO;
    private final BudgetDAO budgetDAO;
   private final TransactionDAO transactionDAO;

   public LedgerCategoryController(UserDAO userDAO,
                                   LedgerCategoryDAO ledgerCategoryDAO,
                                   LedgerDAO ledgerDAO,
                                   BudgetDAO budgetDAO,
                                   TransactionDAO transactionDAO) {
        this.userDAO = userDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.ledgerDAO = ledgerDAO;
        this.budgetDAO = budgetDAO;
        this.transactionDAO = transactionDAO;
    }

    @PostMapping("/create-category")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createCategory(@RequestParam String name,
                                                 Principal principal,
                                                 @RequestParam Long ledgerId,
                                                 @RequestParam CategoryType type) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(ledgerId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Must provide ledgerId");
        }
        Ledger ledger= ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
        if(name == null || name.trim().isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name cannot be empty");
        }
        if(name.length() > 100){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name too long");
        }
        if(ledgerCategoryDAO.existsByLedgerAndName(ledger, name)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Category name must be unique within the ledger");
        }

        LedgerCategory newCategory = new LedgerCategory(name, type, ledger);
        ledgerCategoryDAO.save(newCategory);
        ledger.getCategories().add(newCategory);
        ledgerDAO.save(ledger);
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
        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        if(parentId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Must provide parentId");
        }
        LedgerCategory parent = ledgerCategoryDAO.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
        if (parent.getParent() != null) {
            return ResponseEntity.badRequest().body("Parent must be a Category");
        }
        if(ledgerId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Must provide ledgerId");
        }
        Ledger ledger= ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));

        if(name == null || name.trim().isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name cannot be empty");
        }
        if(name.length() > 100){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name too long");
        }
        if(ledgerCategoryDAO.existsByLedgerAndName(ledger, name)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("SubCategory name must be unique within the ledger");
        }

        LedgerCategory newSubCategory = new LedgerCategory(name, type, ledger);
        newSubCategory.setParent(parent);
        parent.getChildren().add(newSubCategory);
        //parent.addChild(newSubCategory);
        ledgerCategoryDAO.save(newSubCategory);
        ledger.getCategories().add(newSubCategory);
        ledgerDAO.save(ledger);
        ledgerCategoryDAO.save(parent);
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

        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        LedgerCategory category= ledgerCategoryDAO.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (category.getParent() != null) {
            return ResponseEntity.badRequest().body("Must be a Category");
        }

        if (parentId == null || parentId.equals(id)) {
            return ResponseEntity.badRequest().body("Demote must have parentId");
        }

        LedgerCategory parent = ledgerCategoryDAO.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));

        if(parent.getParent() != null){
            return ResponseEntity.badRequest().body("parent must be Category");
        }

        if (!category.getChildren().isEmpty()) {
            return ResponseEntity.badRequest().body("Cannot demote category with subcategories");
        }
        category.setParent(parent);
        parent.getChildren().add(category);

        ledgerCategoryDAO.save(category);
        ledgerCategoryDAO.save(parent);

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
        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category= ledgerCategoryDAO.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        LedgerCategory parent=category.getParent();
        if(parent == null){
            return ResponseEntity.badRequest().body("Must be a SubCategory");
        }

        parent.getChildren().remove(category);
        category.setParent(null);
        ledgerCategoryDAO.save(category);
        ledgerCategoryDAO.save(parent);
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
        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category = ledgerCategoryDAO.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getChildren().isEmpty() && category.getParent() == null) {
            return ResponseEntity.badRequest().body("Cannot delete category with subcategories");
        }

        List<Budget> budgetsToDelete = new ArrayList<>(category.getBudgets());
        budgetDAO.deleteAll(budgetsToDelete);

        if (!deleteTransactions) {
            if(migrateToCategoryId == null) {
                return ResponseEntity.badRequest().body("Must provide migrateToCategoryId");
            }
            LedgerCategory migrateToCategory = ledgerCategoryDAO.findById(migrateToCategoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "migrateToCategory not found"));
            if (migrateToCategory.getParent() != null) {
                return ResponseEntity.badRequest().body("migrateToCategory must be a Category");
            }
            List<Transaction> transactionsToMigrate = new ArrayList<>(category.getTransactions());
            for (Transaction tx : transactionsToMigrate) {
                category.getTransactions().remove(tx);
                tx.setCategory(migrateToCategory);
                migrateToCategory.getTransactions().add(tx);
                transactionDAO.save(tx);
            }
            ledgerCategoryDAO.save(migrateToCategory);
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

                transactionDAO.delete(tx);
            }
        }
        if(category.getParent() != null){
            LedgerCategory parent=category.getParent();
            parent.getChildren().remove(category);
            category.setParent(null);
            ledgerCategoryDAO.save(parent);
        }

        Ledger ledger=category.getLedger();
        ledger.getCategories().remove(category);
        category.setLedger(null);
        ledgerDAO.save(ledger);

        ledgerCategoryDAO.delete(category);
        return ResponseEntity.ok("Deleted successfully");
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
        User user = userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        LedgerCategory category = ledgerCategoryDAO.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if(newName == null || newName.trim().isEmpty()){
            return ResponseEntity.badRequest().body("new name cannot be null");
        }

        if(ledgerCategoryDAO.existsByLedgerAndName(category.getLedger(), newName)){
            if(!ledgerCategoryDAO.findByLedgerAndName(category.getLedger(), newName).getId().equals(category.getId())){
                return ResponseEntity.status(HttpStatus.CONFLICT).body("new name exists already");
            }else{
                return ResponseEntity.ok("Renamed successfully");
            }
        }

        if(newName.length() > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name too long");
        }

        category.setName(newName);
        ledgerCategoryDAO.save(category);


        return ResponseEntity.ok("Renamed successfully");
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
        User user = userDAO.findByUsername(principal.getName());
        if (user == null ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(id.equals(newParentId)){
            return ResponseEntity.badRequest().body("Category cannot be its own parent");
        }
        LedgerCategory category = ledgerCategoryDAO.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        LedgerCategory newParent = ledgerCategoryDAO.findById(newParentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "New parent category not found"));

        if (newParent.getParent() != null) {
            return ResponseEntity.badRequest().body("New parent must be a Category");
        }
        if(category.getParent() == null){
            return ResponseEntity.badRequest().body("Must be a SubCategory");
        }

        LedgerCategory oldParent = category.getParent();
        oldParent.getChildren().remove(category);
        category.setParent(newParent);
        newParent.getChildren().add(category);

        ledgerCategoryDAO.save(oldParent);
        ledgerCategoryDAO.save(newParent);
        ledgerCategoryDAO.save(category);
        return ResponseEntity.ok("Parent category changed successfully");
    }

    @GetMapping("/{id}/all-transactions-for-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Transaction>> getCategoryTransactionsForMonth(@PathVariable Long id,
                                                                             Principal principal,
                                                                             @RequestParam (required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
          if(principal == null){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
          }
          User user = userDAO.findByUsername(principal.getName());
          if (user == null ) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
          }
          LedgerCategory category = ledgerCategoryDAO.findById(id)
                 .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

          if (!category.getLedger().getOwner().getId().equals(user.getId())) {
              return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
          }

          List<Transaction> transactions;
          LocalDate startDate;
          LocalDate endDate;
          if(month == null) {
              startDate = YearMonth.now().atDay(1);
              endDate = YearMonth.now().atEndOfMonth();
          }else{
              startDate = month.atDay(1);
              endDate = month.atEndOfMonth();
          }

          if (category.getParent() == null) { //if it's a category of first level
              List<Long> categoryIds = new ArrayList<>();
              categoryIds.add(category.getId());

              //find all subcategories of this category
              List<LedgerCategory> subCategories = ledgerCategoryDAO.findByParentId(id);
              //add its subCategoryIds and its id to the list of ids categoryIds
              for (LedgerCategory subCategory : subCategories) {
                  categoryIds.add(subCategory.getId());
              }
              //find all transactions of this category and its subcategories
              transactions = transactionDAO.findByCategoryIdsAndUserId(
                      categoryIds, startDate, endDate
              );

          } else { //if it's a subcategory
              //find all transactions of this subcategory
              transactions = transactionDAO.findByCategoryIdAndUserId(
                        id, startDate, endDate
              );
          }

          return ResponseEntity.ok(transactions);
    }

}
