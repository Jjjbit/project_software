package com.ledger.project_software.business;

import com.ledger.project_software.Repository.AccountRepository;
import com.ledger.project_software.Repository.InstallmentPlanRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.Account;
import com.ledger.project_software.domain.CreditAccount;
import com.ledger.project_software.domain.InstallmentPlan;
import com.ledger.project_software.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;

@RestController
@RequestMapping("/installment-plans")
public class InstallmentPlanController {
    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    @PostMapping("/create")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createInstallmentPlan(Principal principal,
                                                @RequestParam BigDecimal totalAmount,
                                                @RequestParam int totalPeriods,
                                                @RequestParam (required = false, defaultValue = "0") Integer paidPeriods,
                                                @RequestParam(required = false, defaultValue = "0.00") BigDecimal feeRate,
                                                @RequestParam(required = false, defaultValue = "EVENLY_SPLIT") InstallmentPlan.FeeStrategy feeStrategy,
                                                @RequestParam Long linkedAccountId) {
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        User user=userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        if(linkedAccountId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Linked account is required");
        }
        Account linkedAccount = accountRepository.findById(linkedAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));
        if (!linkedAccount.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("account does not belong to user");
        }
        if(!(linkedAccount instanceof CreditAccount)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Linked account must be a credit account");
        }
        if(totalAmount.compareTo(BigDecimal.ZERO)<=0 || totalPeriods<=0 || paidPeriods<0 || paidPeriods>totalPeriods || feeRate.compareTo(BigDecimal.ZERO)<0){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid parameters");
        }

        InstallmentPlan installmentPlan = new InstallmentPlan(totalAmount,
                                                              totalPeriods,
                                                              feeRate,
                                                              paidPeriods,
                                                              feeStrategy,
                                                              linkedAccount);
        installmentPlanRepository.save(installmentPlan);
        ((CreditAccount) linkedAccount).addInstallmentPlan(installmentPlan);
        //((CreditAccount) linkedAccount).getInstallmentPlans().add(installmentPlan);
        //((CreditAccount) linkedAccount).setCurrentDebt(((CreditAccount) linkedAccount).getCurrentDebt().add(installmentPlan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
        accountRepository.save(linkedAccount);

        return ResponseEntity.ok("installment plan created successfully");

    }

    @PutMapping("/{id}/edit")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> editInstallmentPlan(Principal principal,
                                                        @PathVariable Long id,
                                                        @RequestParam(required = false) BigDecimal totalAmount,
                                                        @RequestParam(required = false) Integer totalPeriods,
                                                        @RequestParam(required = false) Integer paidPeriods,
                                                        @RequestParam(required = false) BigDecimal feeRate,
                                                        @RequestParam(required = false) InstallmentPlan.FeeStrategy feeStrategy,
                                                        @RequestParam(required = false) Long linkedAccountId){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user=userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        InstallmentPlan installmentPlan = installmentPlanRepository.findById(id).orElse(null);
        if (installmentPlan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Installment plan not found");
        }
        if(! installmentPlan.getLinkedAccount().getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to edit this installment plan");
        }
        BigDecimal oldRemainingAmount = installmentPlan.getRemainingAmount();

        if (totalAmount != null){
            installmentPlan.setTotalAmount(totalAmount);
        }
        if (totalPeriods != null){
            installmentPlan.setTotalPeriods(totalPeriods);
        }
        if (paidPeriods != null){
            installmentPlan.setPaidPeriods(paidPeriods);
        }
        if (feeRate != null){
            installmentPlan.setFeeRate(feeRate);
        }
        if (feeStrategy != null){
            installmentPlan.setFeeStrategy(feeStrategy);
        }
        if (linkedAccountId != null){// change linked account
            Account creditAccount = accountRepository.findById(linkedAccountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));
            if ( !creditAccount.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("account does not belong to user");
            }
            if(!(creditAccount instanceof CreditAccount)){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Linked account must be a credit account");
            }
            if(installmentPlan.getLinkedAccount() != null){
                CreditAccount oldAccount = (CreditAccount) installmentPlan.getLinkedAccount();
                oldAccount.setCurrentDebt(oldAccount.getCurrentDebt().subtract(oldRemainingAmount).setScale(2, RoundingMode.HALF_UP));
                oldAccount.getInstallmentPlans().remove(installmentPlan);
                accountRepository.save(oldAccount);
            }

            ((CreditAccount) creditAccount).addInstallmentPlan(installmentPlan);
            //((CreditAccount) creditAccount).setCurrentDebt(((CreditAccount) creditAccount).getCurrentDebt().add(installmentPlan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
            //((CreditAccount) creditAccount).getInstallmentPlans().add(installmentPlan);
            accountRepository.save(creditAccount);
            installmentPlan.setLinkedAccount(creditAccount);
        }else{ // no change to linked account
            // if other parameters change, need to update the linked account's current debt
            CreditAccount creditAccount = (CreditAccount) installmentPlan.getLinkedAccount();
            creditAccount.setCurrentDebt(creditAccount.getCurrentDebt().subtract(oldRemainingAmount).add(installmentPlan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
            accountRepository.save(creditAccount);
        }
        installmentPlanRepository.save(installmentPlan);

        return ResponseEntity.ok("installment plan updated successfully");

    }

    @DeleteMapping("/{id}/delete")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteInstallmentPlan(Principal principal,
                                                        @PathVariable Long id){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        User user=userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }
        InstallmentPlan installmentPlan = installmentPlanRepository.findById(id).orElse(null);
        if (installmentPlan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Installment plan not found");
        }

        if(! installmentPlan.getLinkedAccount().getOwner().getId().equals(user.getId())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to delete this installment plan");
        }

        CreditAccount creditAccount = (CreditAccount) installmentPlan.getLinkedAccount();
        creditAccount.removeInstallmentPlan(installmentPlan);
        installmentPlan.setLinkedAccount(null);
        //creditAccount.setCurrentDebt(creditAccount.getCurrentDebt().subtract(installmentPlan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
        //creditAccount.getInstallmentPlans().remove(installmentPlan);
        accountRepository.save(creditAccount);

        installmentPlanRepository.delete(installmentPlan);

        return ResponseEntity.ok("installment plan deleted successfully");
    }


}
