package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("BORROWING")
public class BorrowingAccount extends Account{

    @Column
    private boolean isEnded=false; //indica se il borrowing è stato completamente rimborsato
    @Column(name="borrowing_date")
    private LocalDate date;
    @Column
    private BigDecimal borrowingAmount;

    public BorrowingAccount() {}
    public BorrowingAccount(String name,  //person or entity from whom the money is borrowed
                            BigDecimal borrowingAmount, //bilancio da pagare da utente
                            String note,
                            boolean includedInNetWorth,
                            boolean selectable,
                            User owner,
                            LocalDate date) {
        super(name, BigDecimal.ZERO, AccountType.BORROWING, AccountCategory.VIRTUAL_ACCOUNT, owner, note, includedInNetWorth, selectable);
        this.borrowingAmount=borrowingAmount;
        this.date=date;
    }

    public void setBorrowingDate(LocalDate date){this.date=date;}
    @Override
    public void credit(BigDecimal amount) {
        borrowingAmount = borrowingAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        //this.balance = this.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void debit(BigDecimal amount) {
        borrowingAmount = borrowingAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
        //this.balance = this.balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void repay(Transaction tx, BigDecimal amount){
        //balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        borrowingAmount = borrowingAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        incomingTransactions.add(tx);
        checkAndUpdateStatus();
    }

    public void checkAndUpdateStatus() {
        if(borrowingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.isEnded = true;
        } else {
            this.isEnded = false;
        }
    }

    public BigDecimal getBorrowingAmount(){return borrowingAmount;}
}
