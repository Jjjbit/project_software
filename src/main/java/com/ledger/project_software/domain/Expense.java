package com.ledger.project_software.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense")
public class Expense extends Transaction {

    public Expense() {}
    public Expense(LocalDate date,
                   BigDecimal amount,
                   String description,
                   Account account,
                   Ledger ledger,
                   LedgerCategory category) {
        super(date, amount, description, account, null, ledger, category, TransactionType.EXPENSE);
    }
    @Override
    public void execute() {
        if (!fromAccount.hidden && fromAccount.selectable){
            if (!fromAccount.getCategory().equals(AccountCategory.CREDIT) && fromAccount.balance.compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds in the account to execute this transaction.");
            }
            fromAccount.debit(amount);
        }
        fromAccount.getOwner().updateTotalAssets();
        fromAccount.getOwner().updateTotalLiabilities();
        fromAccount.getOwner().updateNetAsset();
    }
    @Override
    public void rollback(){
        fromAccount.credit(amount);
        fromAccount.getOwner().updateTotalAssets();
        fromAccount.getOwner().updateTotalLiabilities();
        fromAccount.getOwner().updateNetAsset();
    }

}
