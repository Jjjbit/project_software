package com.ledger.project_software.business;

import com.ledger.project_software.Repository.BudgetRepository;
import com.ledger.project_software.Repository.LedgerCategoryRepository;
import com.ledger.project_software.Repository.TransactionRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.Budget;
import com.ledger.project_software.domain.CategoryType;
import com.ledger.project_software.domain.LedgerCategory;
import com.ledger.project_software.domain.User;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/budgets")
public class BudgetController {
    private LedgerCategoryRepository ledgerCategoryRepository;
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
        LedgerCategory categoryComponent = null;

        if(categoryComponentId != null){
             categoryComponent= ledgerCategoryRepository.findById(categoryComponentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (!categoryComponent.getLedger().getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Category does not belong to the user");
            }
        }

        Budget budget = new Budget(amount, period, categoryComponent, user);
        if (categoryComponent != null) { //budget for category
            if (categoryComponent.getBudgets().stream()
                    .anyMatch(b -> b.getPeriod() == period && b.isActive(LocalDate.now()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Budget for this category and period already exists");
            }
            if(categoryComponent.getType().equals(CategoryType.INCOME)){
                return ResponseEntity.badRequest().body("Cannot set budget for income category");
            }
            categoryComponent.getBudgets().add(budget);
            //ledgerCategoryRepository.save(categoryComponent);
            budgetRepository.save(budget);
        } else { //uncategorized budget for user
            if (user.getBudgets().stream()
                    .anyMatch(b -> b.getPeriod() == period && b.isActive(LocalDate.now()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Budget for this period already exists");
            }
            user.getBudgets().add(budget);
            budgetRepository.save(budget);
        }

        return ResponseEntity.ok("Budget created successfully");
    }


    @PutMapping("/{id}/edit")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editBudget(@PathVariable Long id,
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

    @PutMapping("{targetBudgetId}/merge")
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

        if(!targetBudget.isActive(LocalDate.now())){
            return ResponseEntity.badRequest().body("Cannot merge into a non-active budget");
        }


        if (targetBudget.getCategory() == null) { //targetBudget is uncategorized user budget
            List<Budget> sourceBudgets = user.getLedgers().stream()
                    .flatMap(l -> l.getCategories().stream())
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le categorie e subcategorie di tutti i ledger di user
                    .filter(b -> b.getCategory().getParent()== null) //esclude categorie di secondo livello
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    //.filter(b -> !b.getId().equals(targetBudgetId)) //esclude target
                    .filter(b -> b.isActive(LocalDate.now()))//solo budget attivi
                    .toList();//tutti i budget di tutte categorie di primo livello attivi in periodo di targetBudget
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));
            budgetRepository.save(targetBudget);
        } else { //budget for category of first level
            if(targetBudget.getCategory().getParent() != null){
                return ResponseEntity.badRequest().body("Target budget must be for a category");
            }
            //merge dei budget di tutte le subcategorie di targetBudget
            List<Budget> sourceBudgets = targetBudget.getCategory().getChildren().stream() //tutte le subcategorie di targetBudget
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le subcategorie di targetBudget
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    .filter(b -> b.isActive(LocalDate.now())) //solo budget attivi
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
        if (budget.isActive(LocalDate.now())) {
            if (budget.getCategory() != null) { //budget for category
                LedgerCategory category = budget.getCategory();
                category.getBudgets().remove(budget);
                budget.setCategory(null);
                //ledgerCategoryComponentRepository.save(category);
            } else { //uncategorized budget
                user.getBudgets().remove(budget);
                //userRepository.save(user);
            }

        }
        budgetRepository.delete(budget);
        return ResponseEntity.ok("Budget deleted successfully");
    }



    //uncategorized user budget + budgets for all categories of first level + spent and remaining for each
    @GetMapping
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

        //uncategorized user budget. return zero values if not present
        Optional<Budget> userBudgetOpt = budgetRepository.findActiveUncategorizedBudgetByUserId(user.getId(), today);

        BigDecimal userSpent;
        LocalDate startDate;
        LocalDate endDate;

        if (userBudgetOpt.isPresent()) {
            Budget budget = userBudgetOpt.get();

            startDate = budget.getStartDateForPeriod(today, budget.getPeriod());
            endDate = budget.getEndDateForPeriod(today, budget.getPeriod());

            userSpent = transactionRepository.sumExpensesByUserAndPeriod(
                    user.getId(),
                    startDate,
                    endDate
            );
        } else {
            userSpent = BigDecimal.ZERO;
            startDate = today.withDayOfMonth(1);
            endDate = today.withDayOfMonth(today.lengthOfMonth());
        }

        //list of Budgets = all categories budget in different ledger. empty list if none present
        //filter only category budgets with same period of uncategorized user budget, or monthly if no uncategorized user budget
        List<Budget> activeBudgets = budgetRepository.findActiveCategoriesBudgetByUserId(user.getId(), today)
                .stream()
                .filter(b -> userBudgetOpt.isEmpty() ? b.getPeriod() == Budget.Period.MONTHLY
                        : b.getPeriod() == userBudgetOpt.get().getPeriod())
                .toList();

        //group by category name to merge budgets of same category in different ledger
        Map<String, List<Budget>> groupedByCategoryName = activeBudgets.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getCategory().getName(),
                        LinkedHashMap::new, // to preserve insertion order
                        Collectors.toList()
                ));

        //for each category name, calculate total budget, spent and remaining
        List<Map<String, Object>> categoryBudgets = groupedByCategoryName.entrySet().stream()
                .map(entry -> {
                    String categoryName = entry.getKey();
                    List<Budget> budgets = entry.getValue();

                    List<Long> categoryIds = budgets.stream()
                            .map(b -> b.getCategory().getId())
                            .toList();

                    BigDecimal totalBudget = budgets.stream()
                            .map(Budget::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal spent = transactionRepository.sumExpensesByCategoryIdsAndPeriod(
                            user.getId(),
                            categoryIds,
                            startDate,
                            endDate
                    );

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("categoryName", categoryName);
                    map.put("amount", totalBudget);
                    map.put("spent", spent);
                    map.put("remaining", totalBudget.subtract(spent));
                    return map;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userBudget", userBudgetOpt.map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("amount", b.getAmount());
            map.put("spent", userSpent);
            map.put("remaining", b.getAmount().subtract(userSpent));
            return map;
        }).orElseGet(() -> { //if no uncategorized user budget
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("amount", BigDecimal.ZERO);
            map.put("spent", BigDecimal.ZERO);
            map.put("remaining", BigDecimal.ZERO);
            return map;
        }));

        response.put("categoryBudgets", categoryBudgets);

        return ResponseEntity.ok(response);
    }

    //budget for category of first level + spent and remaining + budgets for all its subcategories + spent and remaining for each
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map <String, Object>> getCategoryBudgetsWithSubCategoryBudgets(@PathVariable Long id,
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

        String parentName = categoryBudget.getCategory().getName();

        //get all categories of first level with same name of categoryBudget.getCategory() from all ledgers of user
        List<LedgerCategory> allSameNameParents = user.getLedgers().stream()
                .flatMap(l -> l.getCategories().stream())
                .filter(c -> c.getName().equalsIgnoreCase(parentName) && c.getParent() == null)
                .toList();

        //sum category budget in allSameNameParents and spent in period of categoryBudget
        BigDecimal totalParentAmount = BigDecimal.ZERO;
        BigDecimal totalParentSpent = BigDecimal.ZERO;

        for (LedgerCategory parentCat : allSameNameParents) {
            List<LedgerCategory> children = parentCat.getChildren(); //subcategories of parentCat
            List<Long> categoryIds = new ArrayList<>();
            categoryIds.add(parentCat.getId());
            categoryIds.addAll(children.stream().map(LedgerCategory::getId).toList());

            //get active budget of category with same period of categoryBudget
            Optional<Budget> parentBudgetOpt = budgetRepository.findActiveCategoryBudget(
                    user.getId(),
                    parentCat.getId(),
                    today,
                    categoryBudget.getPeriod()
            );
            if (parentBudgetOpt.isPresent()) {
                Budget b = parentBudgetOpt.get();
                totalParentAmount = totalParentAmount.add(b.getAmount());

                BigDecimal spent = transactionRepository.sumExpensesByCategoryIdsAndPeriod(
                        user.getId(),
                        categoryIds,
                        b.getStartDateForPeriod(today, b.getPeriod()),
                        b.getEndDateForPeriod(today, b.getPeriod())
                );

                if (spent == null) {
                    spent = BigDecimal.ZERO;
                }

                totalParentSpent = totalParentSpent.add(spent);
            }
        }

        //get all subcategories of allSameNameParents
        List<LedgerCategory> allSubCategories = allSameNameParents.stream()
                .flatMap(p -> p.getChildren().stream())
                .toList();

        //group by name to merge subcategories with same name and same parent in different ledgers
        Map<String, List<LedgerCategory>> groupedSubCats = allSubCategories.stream()
                .collect(Collectors.groupingBy(
                        LedgerCategory::getName,
                        LinkedHashMap::new, // to preserve insertion order
                        Collectors.toList()
                ));

        //for each subcategory name, calculate total budget, spent and remaining
        List<Map<String, Object>> subCategoryBudgets = groupedSubCats.entrySet().stream()
                .map(entry -> {
                    String subName = entry.getKey();
                    List<LedgerCategory> sameSubCats = entry.getValue();

                    BigDecimal totalAmount = BigDecimal.ZERO;
                    BigDecimal totalSpent = BigDecimal.ZERO;

                    for (LedgerCategory subCat : sameSubCats) {
                        Optional<Budget> subBudgetOpt = budgetRepository.findActiveSubCategoryBudget(
                                user.getId(),
                                subCat.getId(),
                                today,
                                categoryBudget.getPeriod()
                        );

                        if (subBudgetOpt.isPresent()) {
                            totalAmount = totalAmount.add(subBudgetOpt.get().getAmount());
                        }

                        BigDecimal spent = transactionRepository.sumExpensesBySubCategoryAndPeriod(
                                user.getId(),
                                subCat.getId(),
                                categoryBudget.getStartDateForPeriod(today, categoryBudget.getPeriod()),
                                categoryBudget.getEndDateForPeriod(today, categoryBudget.getPeriod())
                        );

                        if (spent == null) {
                            spent = BigDecimal.ZERO;
                        }

                        totalSpent = totalSpent.add(spent);

                    }

                    Map<String, Object> map = new HashMap<>();
                    map.put("subCategory", subName);
                    map.put("amount", totalAmount);
                    map.put("spent", totalSpent);
                    map.put("remaining", totalAmount.subtract(totalSpent));
                    return map;
                }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("category", parentName);
        response.put("amount", totalParentAmount);
        response.put("spent", totalParentSpent);
        response.put("remaining", totalParentAmount.subtract(totalParentSpent));

        response.put("subCategoryBudgets", subCategoryBudgets);

        return ResponseEntity.ok(response);
    }
}
