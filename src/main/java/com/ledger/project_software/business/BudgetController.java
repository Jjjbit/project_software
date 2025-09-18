package com.ledger.project_software.business;

import com.ledger.project_software.Repository.BudgetRepository;
import com.ledger.project_software.Repository.LedgerCategoryRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.Budget;
import com.ledger.project_software.domain.LedgerCategory;
import com.ledger.project_software.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/budgets")
public class BudgetController {
    private final LedgerCategoryRepository ledgerCategoryRepository;
    private TransactionRepository transactionRepository;
    private BudgetRepository budgetRepository;

    private UserRepository userRepository;


    public BudgetController(BudgetRepository budgetRepository,
                            UserRepository userRepository,
                            LedgerCategoryRepository ledgerCategoryRepository,
                            TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.ledgerCategoryRepository = ledgerCategoryRepository;
        this.transactionRepository = transactionRepository;

    }

    @PostMapping("/create")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createBudget(@RequestParam BigDecimal amount,
                                               @RequestParam(required = false) Long categoryComponentId,
                                               Principal principal,
                                               @RequestParam Budget.Period period) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Amount must be non-negative");
        }

        LedgerCategory categoryComponent = ledgerCategoryRepository.findById(categoryComponentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CategoryComponent not found"));

        Budget budget = new Budget(amount, period, categoryComponent, user);
        if (categoryComponent != null) {
            if (categoryComponent.getBudgets().stream()
                    .anyMatch(b -> b.getPeriod() == period && b.isInPeriod(LocalDate.now()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Budget for this category and period already exists");
            }
            categoryComponent.getBudgets().add(budget);
            ledgerCategoryRepository.save(categoryComponent);
            budgetRepository.save(budget);
        } else {
            if (user.getBudgets().stream()
                    .anyMatch(b -> b.getPeriod() == period && b.isInPeriod(LocalDate.now()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Budget for this period already exists");
            }
            user.getBudgets().add(budget);
            userRepository.save(user);
            budgetRepository.save(budget);
        }

        return ResponseEntity.ok("Budget created successfully");
    }


    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> updateBudget(@PathVariable Long id,
                                               @RequestParam BigDecimal amount,
                                               Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (!budget.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Budget does not belong to the user");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Amount must be non-negative");
        }

        budget.setAmount(amount);
        budgetRepository.save(budget);
        return ResponseEntity.ok("Budget updated successfully");
    }

    @PostMapping("{targetBudgetId}/merge")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> mergeBudgets(@PathVariable Long targetBudgetId,
                                               Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        Budget targetBudget = budgetRepository.findById(targetBudgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target budget not found"));

        if (!targetBudget.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Target budget does not belong to the user");
        }

        if(!targetBudget.isInPeriod(LocalDate.now())){
            return ResponseEntity.badRequest().body("Cannot merge into a non-active budget");
        }


        if (targetBudget.getCategory() == null) { //budget totale ovvero budget for user
            List<Budget> sourceBudgets = user.getLedgers().stream()
                    .flatMap(l -> l.getCategories().stream())
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le categorie e subcategorie di tutti i ledger di user
                    .filter(b -> b.getCategory().getParent()== null) //esclude categorie di secondo livello
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    //.filter(b -> !b.getId().equals(targetBudgetId)) //esclude target
                    .filter(b -> b.isInPeriod(LocalDate.now()))//solo budget attivi
                    .toList();//tutti i budget di tutte categorie di primo livello attivi in periodo di targetBudget
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));
            budgetRepository.save(targetBudget);
        } else { //budget for categoria di primo livello
            if(targetBudget.getCategory().getParent() != null){
                return ResponseEntity.badRequest().body("Target budget must be for a category");
            }
            //merge dei budget di tutte le subcategorie di targetBudget
            List<Budget> sourceBudgets = targetBudget.getCategory().getChildren().stream() //tutte le subcategorie di targetBudget
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le subcategorie di targetBudget
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    .filter(b -> b.isInPeriod(LocalDate.now())) //solo budget attivi
                    .toList();//tutti i budget di tutte le subcategorie di targetBudget non attivi in periodo di targetBudget
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));
            budgetRepository.save(targetBudget);
        }

        return ResponseEntity.ok("Budgets merged successfully");
    }

    @DeleteMapping("/{id}/delete")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteBudget(@PathVariable Long id,
                                               Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthenticated access");
        }
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (!budget.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Budget does not belong to the user");
        }
        if (budget.isInPeriod(LocalDate.now())) {
            if (budget.getCategory() != null) {
                LedgerCategory category = budget.getCategory();
                category.getBudgets().remove(budget);
                budget.setCategory(null);
                //ledgerCategoryComponentRepository.save(category);
            } else {
                user.getBudgets().remove(budget);
                //userRepository.save(user);
            }

        }
        budgetRepository.delete(budget);
        return ResponseEntity.ok("Budget deleted successfully");
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getAllBudgets(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LocalDate today = LocalDate.now();
        //LocalDate today = LocalDate.now(clock);

        Optional<Budget> userBudgetOpt = budgetRepository.findActiveUserBudget(user.getId(), today);
        BigDecimal userSpent = userBudgetOpt.map(budget ->
                transactionRepository.sumExpensesByUserAndPeriod(
                        user.getId(),
                        budget.getStartDateForPeriod(today, budget.getPeriod()),
                        budget.getEndDateForPeriod(today, budget.getPeriod())
                )
        ).orElse(BigDecimal.ZERO);

        List<Map<String, Object>> categoryBudgets = budgetRepository.findActiveCategoriesBudgetByUserId(user.getId(), today)
                .stream()
                .map(budget -> {
                    List<Long> categoryIds = new ArrayList<>();
                    categoryIds.add(budget.getCategory().getId());
                    categoryIds.addAll(budget.getCategory().getChildren().stream()
                            .map(LedgerCategory::getId)
                            .toList());

                    BigDecimal spent = transactionRepository.sumExpensesByCategoryAndPeriod(
                            user.getId(),
                            categoryIds,
                            budget.getStartDateForPeriod(today, budget.getPeriod()),
                            budget.getEndDateForPeriod(today, budget.getPeriod())
                    );
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", budget.getCategory().getName());
                    map.put("amount", budget.getAmount());
                    map.put("spent", spent);
                    map.put("remaining", budget.getAmount().subtract(spent));
                    return map;
                }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("userBudget", userBudgetOpt.map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", b.getAmount());
            map.put("spent", userSpent);
            map.put("remaining", b.getAmount().subtract(userSpent));
            return map;
        }).orElseGet(() -> {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", BigDecimal.ZERO);
            map.put("spent", BigDecimal.ZERO);
            map.put("remaining", BigDecimal.ZERO);
            return map;
        }));

        response.put("categoryBudgets", categoryBudgets);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map <String, Object>> getSubCategoryBudgets(@PathVariable Long id,
                                                                      Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Budget categoryBudget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (!categoryBudget.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (categoryBudget.getCategory() == null) {
            return  ResponseEntity.badRequest().build();
        }
        if(!(categoryBudget.getCategory().getParent() == null)){
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        Map<String, Object> response= new HashMap<>();
        List<LedgerCategory> subCategories =  categoryBudget.getCategory().getChildren();
        //Id delle subcategorie
        List<Long> subCategoryIds = subCategories.stream()
                .map(LedgerCategory::getId)
                .toList();
        List<Long> allCategoryIds = new ArrayList<>(); //Id di categoria + subcategorie
        allCategoryIds.add(categoryBudget.getCategory().getId());
        allCategoryIds.addAll(subCategoryIds);

        BigDecimal spent = transactionRepository.sumExpensesByCategoryAndPeriod( //tutte le spese fatte in periodo per categoria + subcategorie
                user.getId(),
                allCategoryIds,
                categoryBudget.getStartDateForPeriod(today, categoryBudget.getPeriod()),
                categoryBudget.getEndDateForPeriod(today, categoryBudget.getPeriod())
        );
        response.put("category", categoryBudget.getCategory().getName());
        response.put("amount", categoryBudget.getAmount());
        response.put("spent", spent);
        response.put("remaining", categoryBudget.getAmount().subtract(spent));


        if (!subCategories.isEmpty()) {
            List<Map<String, Object>> subCategoryBudgets = subCategories.stream().map(subCat -> {

                Optional<Budget> subBudgetOpt = budgetRepository.findActiveSubCategoryBudget(user.getId(), today)
                        .filter(b -> b.getCategory().getId().equals(subCat.getId()));

                BigDecimal subSpent = transactionRepository.sumExpensesBySubCategoryAndPeriod( //spese fatte in periodo per subcategoria
                        user.getId(),
                        subCat.getId(),
                        categoryBudget.getStartDateForPeriod(today, categoryBudget.getPeriod()),
                        categoryBudget.getEndDateForPeriod(today, categoryBudget.getPeriod())
                );

                Map<String, Object> map = new HashMap<>();
                map.put("subCategory", subCat.getName());
                if (subBudgetOpt.isPresent()) {
                    Budget subBudget = subBudgetOpt.get();
                    map.put("amount", subBudget.getAmount());
                    map.put("spent", subSpent);
                    map.put("remaining", subBudget.getAmount().subtract(subSpent));
                } else {
                    map.put("amount", BigDecimal.ZERO);
                    map.put("spent", subSpent);
                }
                return map;
            }).toList();

            response.put("subCategoryBudgets", subCategoryBudgets);
        }

        return ResponseEntity.ok(response);
    }
}
