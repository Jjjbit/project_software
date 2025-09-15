package com.ledger.project_software.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transfer")
public class Transfer extends Transaction{

    public Transfer() {}
    public Transfer(LocalDate date,
                    String description,
                    Account from,
                    Account to,
                    BigDecimal amount,
                    Ledger ledger) {
        super(date, amount, description, from, to, ledger, null, TransactionType.TRANSFER);
    }

    public void execute() {
        if (fromAccount.equals(toAccount)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }
        if(fromAccount ==null && toAccount==null){
            throw new IllegalArgumentException("select account");
        }else {
            if (!fromAccount.selectable || !toAccount.selectable || fromAccount.hidden || toAccount.hidden) {
                throw new IllegalStateException("Accounts are not valid for transfer.");
            }
        }

        fromAccount.debit(amount);
        if(toAccount !=null) {
            toAccount.credit(amount);
        }
        fromAccount.getOwner().updateTotalAssets();
        fromAccount.getOwner().updateTotalLiabilities();
        fromAccount.getOwner().updateNetAsset();
    }

    @Override
    public void rollback(){
        fromAccount.credit(amount);
        if(toAccount != null) {
            toAccount.debit(amount);
        }
        fromAccount.getOwner().updateTotalAssets();
        fromAccount.getOwner().updateTotalLiabilities();
        fromAccount.getOwner().updateNetAsset();
    }
}
