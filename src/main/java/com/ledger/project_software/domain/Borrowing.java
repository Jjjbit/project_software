package com.ledger.project_software.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("BORROWING")
public class Borrowing extends BorrowingAndLending {
    //@OneToMany(mappedBy = "borrowing", orphanRemoval = true)
    @OneToMany(orphanRemoval = true)
    @JoinColumn(name = "depositAccount_id") //aggiungere la colonna depositAccount_id nella tabella Borrowing
    private List<Account> depositAccounts; //depositAccount può essere null

    public Borrowing() {}
    public Borrowing(String lender, BigDecimal amount, Account depositAccount, String notes, User owner, Ledger ledger) {
        super(lender, amount, notes, owner, ledger);
        depositAccounts = new ArrayList<>();
        depositAccounts.add(depositAccount);
        depositAccount.credit(amount);
        if(includedInNetWorth) {
            owner.updateTotalLiabilities();
            owner.updateNetAsset();
        }
    }

    @Override
    public boolean isIncoming() {
        return true;
    }

    public void repay(BigDecimal amount, Account account) {
        repaidAmount = repaidAmount.add(amount);
        account.debit(amount);
        checkAndUpdateStatus();
    }

    public void addIncoming(BigDecimal additionalAmount, Account toAccount) {
        totalAmount = totalAmount.add(additionalAmount);
        if (toAccount != null) {
            toAccount.credit(additionalAmount);
            depositAccounts.add(toAccount);
        }
    }

}
