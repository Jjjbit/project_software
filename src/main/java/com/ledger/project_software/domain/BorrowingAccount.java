package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("BORROWING")
public class BorrowingAccount extends Account{

    @Column
    private boolean isEnded=false; //indica se il borrowing è stato completamente rimborsato
    @Column(name="borrowing_date")
    private LocalDate date;

    public BorrowingAccount() {}
    public BorrowingAccount(String name,  //person or entity from whom the money is borrowed
                            BigDecimal balance, //bilancio da pagare da utente
                            String note,
                            boolean includedInNetWorth,
                            boolean selectable,
                            User owner,
                            LocalDate date) {
        super(name, balance, AccountType.BORROWING, AccountCategory.VIRTUAL_ACCOUNT, owner, note, includedInNetWorth, selectable);
        this.date=date;
    }

    public void setBorrowingDate(LocalDate date){this.date=date;}
    @Override
    public void credit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    @Override
    public void debit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void repay(BigDecimal amount, Account fromAccount, Ledger ledger) {
        if(fromAccount != null){
            fromAccount.debit(amount); //decrementa il balance dell'account
        }
        String description;
        if(fromAccount != null) {
            description = fromAccount.getName() + "to" + name;
        } else {
            description = "External account to " + name;
        }
        Transaction tx = new Transfer(
                LocalDate.now(),
                description,
                fromAccount,
                this,
                amount,
                ledger
        );
        balance = balance.subtract(amount); //decrementa il balance del borrowing
        this.getIncomingTransactions().add(tx); //aggiunge la transazione alla lista delle transazioni in entrata del borrowing
        if(fromAccount != null) {
            fromAccount.debit(amount); //decrementa il balance dell'account
            fromAccount.getOutgoingTransactions().add(tx); //aggiunge la transazione alla lista delle transazioni in uscita dell'account
        }
        if(ledger != null) {
            ledger.getTransactions().add(tx); //aggiunge la transazione alla lista delle transazioni del ledger
        }
        // controlla se il borrowing è stato completamente rimborsato
        checkAndUpdateStatus();
    }

    public void checkAndUpdateStatus() {
        if(balance.compareTo(BigDecimal.ZERO) <= 0) {
            this.isEnded = true;
        } else {
            this.isEnded = false;
        }
    }
}
