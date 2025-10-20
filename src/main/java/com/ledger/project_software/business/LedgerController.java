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
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/ledgers")
public class LedgerController {
    private final LedgerDAO ledgerDAO;
    private final UserDAO userDAO;
    private final CategoryDAO categoryDAO;
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionRepository;
    private final LedgerCategoryDAO ledgerCategoryRepository;

    public LedgerController(LedgerDAO ledgerDAO,
                            UserDAO userDAO,
                            CategoryDAO categoryDAO,
                            AccountDAO accountDAO,
                            TransactionDAO transactionRepository,
                            LedgerCategoryDAO ledgerCategoryRepository) {
        this.ledgerDAO = ledgerDAO;
        this.userDAO = userDAO;
        this.categoryDAO = categoryDAO;
        this.accountDAO = accountDAO;
        this.transactionRepository = transactionRepository;
        this.ledgerCategoryRepository = ledgerCategoryRepository;
    }

    @PostMapping("/create")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createLedger(@RequestParam String name,
                                               Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        if(ledgerDAO.findByName(name) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ledger name already exists");
        }

        Ledger ledger=new Ledger(name, owner);
        ledgerDAO.save(ledger);
        owner.getLedgers().add(ledger);

        List<Category> templateCategories = categoryDAO.findByParentIsNull();

        //copia albero di categorycomponent dal database a ledger
        for (Category template : templateCategories) {
            LedgerCategory ledgerCategory= copyCategory(template, ledger);
            ledger.getCategories().add(ledgerCategory);
        }
        return ResponseEntity.ok("ledger created successfully");
    }

    private LedgerCategory copyCategory(Category template, Ledger ledger) {
        LedgerCategory copy;

        if(template.getParent() != null) { //categoria di secondo livello
            copy = new LedgerCategory();
            copy.setName(template.getName());
            copy.setType(template.getType());
            copy.setLedger(ledger);
            ledger.getCategories().add(copy);
        } else { //categoria di primo livello
            copy = new LedgerCategory();
            copy.setName(template.getName());
            copy.setType(template.getType());
            copy.setLedger(ledger);

            for(Category child : template.getChildren()) {
                LedgerCategory childCopy = copyCategory(child, ledger);
                copy.getChildren().add(childCopy);
                //copy.addChild(childCopy);
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
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        Ledger ledger = ledgerDAO.findById(ledgerId).orElse(null);
        if (ledger == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
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
                    accountDAO.save(to); //salva modifiche a account
                }
            } else if (tx instanceof Expense) {
                if(from != null) {
                    from.credit(tx.getAmount());
                    from.getOutgoingTransactions().remove(tx);
                    tx.setFromAccount(null);
                    accountDAO.save(from);
                }
            } else if (tx instanceof Transfer) {
                if(from != null) {
                    from.credit(tx.getAmount());
                    from.getOutgoingTransactions().remove(tx);
                    tx.setFromAccount(null);
                    accountDAO.save(from);
                }
                if(to != null) {
                    to.debit(tx.getAmount());
                    to.getIncomingTransactions().remove(tx);
                    tx.setToAccount(null);
                    accountDAO.save(to);
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
        ledgerDAO.delete(ledger);
        return ResponseEntity.ok("Ledger deleted successfully");
    }


    @PostMapping("/{ledgerId}/copy")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> copyLedger(@PathVariable Long ledgerId,
                                             Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        if(ledgerId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ledger ID");
        }
        Ledger ledger = ledgerDAO.findById(ledgerId).orElse(null);
        if (ledger == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
        }

        if(!ledger.getOwner().getId().equals(owner.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to copy this ledger");
        }

        Ledger newLedger=new Ledger(ledger.getName()+ " Copy", owner);
        owner.getLedgers().add(newLedger);

        for (LedgerCategory oldCategory : ledger.getCategories()) {
            if(oldCategory.getParent() == null) { //solo categorie di primo livello
                LedgerCategory newCategory = copyLedgerCategory(oldCategory, newLedger);
                newLedger.getCategories().add(newCategory);
            }

        }

        ledgerDAO.save(newLedger);
        return ResponseEntity.ok("copy ledger");
    }
    private LedgerCategory copyLedgerCategory(LedgerCategory oldCategory, Ledger newLedger) {
        LedgerCategory copy;

        if( oldCategory.getParent() != null) { //categoria di secondo livello
            copy = new LedgerCategory();
            copy.setName(oldCategory.getName());
            copy.setType(oldCategory.getType());
            copy.setLedger(newLedger);
            newLedger.getCategories().add(copy);
        } else { //categoria di primo livello
            copy = new LedgerCategory();
            copy.setName(oldCategory.getName());
            copy.setType(oldCategory.getType());
            copy.setLedger(newLedger);

            for(LedgerCategory child : oldCategory.getChildren()) {
                LedgerCategory childCopy = copyLedgerCategory(child, newLedger);
                copy.getChildren().add(childCopy);
                childCopy.setParent(copy);
            }
        }
        return copy;
    }

    @PutMapping("/{ledgerId}/rename")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> renameLedger(@PathVariable Long ledgerId,
                                               @RequestParam String newName,
                                               Principal principal) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User owner= userDAO.findByUsername(principal.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        if(ledgerId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ledger ID");
        }
        Ledger ledger = ledgerDAO.findById(ledgerId).orElse(null);
        if (ledger == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ledger not found");
        }
        if(!ledger.getOwner().getId().equals(owner.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to rename this ledger");
        }

        if(newName == null || newName.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ledger name cannot be empty");
        }

        if(ledgerDAO.findByName(newName) != null) {
            if(!ledgerDAO.findByName(newName).getId().equals(ledgerId)) {//se il nome esiste ma appartiene ad un altro ledger
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Ledger name already exists");
            }else{
                return ResponseEntity.ok("Ledger renamed successfully");
            }
        }

        ledger.setName(newName);
        ledgerDAO.save(ledger);
        return ResponseEntity.ok("Ledger renamed successfully");
    }



    @GetMapping("/all-ledgers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Ledger>> getAllLedgers(Principal principal) {
        if(principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Ledger> ledgers = ledgerDAO.findByOwner(user);
        return ResponseEntity.ok(ledgers);
    }

    @GetMapping("/{ledgerId}/all-transactions-for-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Transaction>> getLedgerTransactionsForMonth(@PathVariable Long ledgerId,
                                                              Principal principal,
                                                              @RequestParam (required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        if(principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if(ledgerId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Ledger ledger = ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
        if(!ledger.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        LocalDate startDate;
        LocalDate endDate;
        if(month == null) {
            startDate = YearMonth.now().atDay(1);
            endDate = YearMonth.now().atEndOfMonth();
        }else{
            startDate = month.atDay(1);
            endDate = month.atEndOfMonth();
        }

        List<Transaction> transactions = transactionRepository.findByLedgerIdAndOwnerId(
                ledgerId,
                user.getId(),
                startDate,
                endDate);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{ledgerId}/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getLedgerCategories(@PathVariable Long ledgerId,
                                                              Principal principal) {
        if(principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Ledger ledger = ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));
        if(!ledger.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        //find parent categories (categories without parent) of ledger
        List<LedgerCategory> parentCategories = ledgerCategoryRepository.findByLedgerIdAndParentIsNull(ledgerId);

        //for each parent category, find its subcategories
        List<Map<String, Object>> categories = parentCategories.stream().map(parent -> {
            Map<String, Object> parentMap = new LinkedHashMap<>();
            parentMap.put("CategoryName", parent.getName());

            //subCategoriesFromDb is a list of categories that have parent id = parent.getId()
            List<LedgerCategory> subCategoriesFromDb = ledgerCategoryRepository.findByParentId(parent.getId());

            //subCategories is a list of maps with id and name of each subcategory of parent id = parent.getId()
            List<Map<String, Object>> subCategories = subCategoriesFromDb.stream()
                    .map(child -> {
                        Map<String, Object> childMap = new LinkedHashMap<>();
                        childMap.put("SubCategoryName", child.getName());
                        return childMap;
                    }).toList();

            parentMap.put("subCategories", subCategories);
            return parentMap;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ledgerName", ledger.getName());
        response.put("categories", categories);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ledgerId}/monthly-summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMonthlySummary(@PathVariable Long ledgerId,
                                                                 @RequestParam (required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                                 Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userDAO.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (ledgerId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Ledger ledger = ledgerDAO.findById(ledgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger not found"));

        if (!ledger.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        LocalDate startDate;
        LocalDate endDate;
        if(month == null) {
            startDate = YearMonth.now().atDay(1);
            endDate = YearMonth.now().atEndOfMonth();
            month = YearMonth.now();
        }else{
            startDate = month.atDay(1);
            endDate = month.atEndOfMonth();
        }


        BigDecimal totalIncome = transactionRepository.sumIncomeByLedgerAndPeriod(ledgerId, startDate, endDate);
        BigDecimal totalExpense = transactionRepository.sumExpenseByLedgerAndPeriod(ledgerId, startDate, endDate);

        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ledgerName", ledger.getName());
        response.put("month", month.toString());
        response.put("totalIncome", totalIncome);
        response.put("totalExpense", totalExpense);

        return ResponseEntity.ok(response);
    }


}
