package com.ledger.project_software.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("LENDING")
public class Lending extends BorrowingAndLending {
    //@OneToMany(mappedBy = "lending", orphanRemoval = true)
    @OneToMany(orphanRemoval = true)
    @JoinColumn(name = "paymentAccount_id")
    private List<Account> paymentAccounts;

    public Lending() {}
    public Lending(String borrower, BigDecimal amount, Account paymentAccount, String notes, User owner, Ledger ledger) {
        super(borrower, amount, notes, owner, ledger);
        paymentAccounts=new ArrayList<>();
        paymentAccounts.add(paymentAccount);
        paymentAccount.debit(amount);
        if(!includedInNetWorth) {
            owner.updateTotalAssets();
            owner.updateNetAsset();
        }
    }

    @Override
    public boolean isIncoming() {
        return false;
    }

    public void receiveRepayment(BigDecimal amount, Account account) {
        repaidAmount = repaidAmount.add(amount);
        account.credit(amount);
        checkAndUpdateStatus();
    }

    public void addOutgoing(BigDecimal additionalAmount, Account fromAccount) {
        totalAmount = totalAmount.add(additionalAmount);
        if (fromAccount != null) {
            fromAccount.debit(additionalAmount);
            paymentAccounts.add(fromAccount);
        }
    }
}
