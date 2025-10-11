package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("LENDING")
public class LendingAccount extends Account {
    @Column
    private boolean isEnded = false; //indica se il borrowing è stato completamente rimborsato

    @Column(name="lending_date")
    private LocalDate date;

    public LendingAccount() {}
    public LendingAccount(String name,  //person or entity from whom the money is lent
                          BigDecimal balance, //bilancio da pagare da utente
                          String note,
                          boolean includedInNetWorth,
                          boolean selectable,
                          User owner,
                          LocalDate date) {
        super(name, balance, AccountType.LENDING, AccountCategory.VIRTUAL_ACCOUNT, owner, note, includedInNetWorth, selectable);
        this.date = date;
    }

    public void receiveRepayment(Transaction tx, BigDecimal amount){
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        outgoingTransactions.add(tx);
        checkAndUpdateStatus();
    }

    public void receiveRepayment(BigDecimal amount, Account toAccount, Ledger ledger) {
        String description;
        if (toAccount != null) {
            description = name + " to " + toAccount.getName();
        } else {
            description = name + " to External account";
        }
        Transaction tx = new Transfer(
                LocalDate.now(),
                description,
                this,
                toAccount,
                amount,
                ledger
        );
        this.balance = this.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        this.getOutgoingTransactions().add(tx);
        if (toAccount != null) {
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(tx);
        }
        if (ledger != null){
            ledger.getTransactions().add(tx);
        }
        checkAndUpdateStatus();
    }
    public void setLendingDate(LocalDate date) {
        this.date = date;
    }
    @Override
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void checkAndUpdateStatus() {
        if(balance.compareTo(BigDecimal.ZERO) <= 0) {
            this.isEnded = true;
        } else {
            this.isEnded = false;
        }
    }
}
