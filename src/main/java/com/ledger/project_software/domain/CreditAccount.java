package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_account")
public class CreditAccount extends Account {

    @Column(name = "credit_limit", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditLimit= BigDecimal.ZERO;

    @Column(name = "current_debt", precision = 15, scale = 2, nullable = true)
    private BigDecimal currentDebt = BigDecimal.ZERO;

    @Column(name = "bill_date")
    private Integer billDay=null;

    @Column(name = "due_date")
    private Integer dueDay=null;

    @OneToMany(mappedBy = "linkedAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InstallmentPlan> installmentPlans;

    public CreditAccount(){}
    public CreditAccount(String name,
                         BigDecimal balance,
                         User owner,
                         String notes,
                         boolean includedInNetWorth,
                         boolean selectable,
                         BigDecimal creditLimit,
                         BigDecimal currentDebt, //contiene importo residuo delle rate
                         Integer billDate,
                         Integer dueDate,
                         AccountType type) {
        super(name, balance, type, AccountCategory.CREDIT, owner, notes, includedInNetWorth, selectable);
        this.creditLimit = creditLimit;
        if(currentDebt == null){
            this.currentDebt=BigDecimal.ZERO;
        }else {
            this.currentDebt = currentDebt;
        }
        this.billDay = billDate;
        this.dueDay = dueDate;
        this.installmentPlans = new ArrayList<>();
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }
    public BigDecimal getCreditLimit(){return creditLimit;}
    public Integer getBillDay(){return billDay;}
    public Integer getDueDay(){return dueDay;}
    public void setCurrentDebt(BigDecimal currentDebt) {
        this.currentDebt = currentDebt;
    }
    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }
    public void setBillDay(Integer billDate) {
        this.billDay = billDate;
    }
    public void setDueDay(Integer dueDate) {
        this.dueDay = dueDate;
    }

    @Override
    public void debit(BigDecimal amount) {
        if (amount.compareTo(balance) > 0) { //amount>balance
            if (currentDebt.add(amount.subtract(balance)).compareTo(creditLimit) > 0) { //currentDebt+(amount-balance)>creditLimit
                throw new IllegalArgumentException("Amount exceeds credit limit");
            } else {
                currentDebt = currentDebt.add(amount.subtract(balance)).setScale(2, RoundingMode.HALF_UP);
                balance = BigDecimal.ZERO;
            }
        } else {
            balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public void repayDebt(Transaction tx){
        incomingTransactions.add(tx);
        currentDebt = currentDebt.subtract(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (currentDebt.compareTo(BigDecimal.ZERO) < 0) {
            currentDebt = BigDecimal.ZERO;
        }
    }
    /*public void repayDebt(BigDecimal amount, Account fromAccount, Ledger ledger) {
        Transaction tx = new Transfer(
                LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                this,
                amount,
                ledger
        );
        incomingTransactions.add(tx);
        if(ledger != null) {
            ledger.getTransactions().add(tx);
        }

        if(fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.outgoingTransactions.add(tx);
        }
        currentDebt = currentDebt.subtract(amount).setScale(2, BigDecimal.ROUND_HALF_UP);
        if (currentDebt.compareTo(BigDecimal.ZERO) < 0) {
            currentDebt = BigDecimal.ZERO;
        }
    }*/

    public List<InstallmentPlan> getInstallmentPlans() {
        return installmentPlans;
    }
    public void addInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.add(installmentPlan);
        currentDebt = currentDebt.add(installmentPlan.getRemainingAmountWithRepaidPeriods()).setScale(2, RoundingMode.HALF_UP);
    }
    public void removeInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.remove(installmentPlan);
        currentDebt = currentDebt.subtract(installmentPlan.getRemainingAmountWithRepaidPeriods()).setScale(2, RoundingMode.HALF_UP);
    }
    public void repayInstallmentPlan(InstallmentPlan installmentPlan){
        BigDecimal amount = installmentPlan.getMonthlyPayment(installmentPlan.getPaidPeriods() + 1);
        currentDebt = currentDebt.subtract(amount).setScale(2, RoundingMode.HALF_UP);

    }
}
