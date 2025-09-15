package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
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
                         Integer dueDate, AccountType type) {
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
        this.owner.addAccount(this);
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
                balance = BigDecimal.ZERO;
                //addNewDebt(amount.subtract(balance));
                currentDebt = currentDebt.add(amount.subtract(balance));
            }
        } else {
            balance = balance.subtract(amount);
        }
        this.owner.updateTotalLiabilities();
        this.owner.updateTotalAssets();
        this.owner.updateNetAsset();
    }

    public void repayDebt(BigDecimal amount, Account fromAccount) {
        currentDebt = currentDebt.subtract(amount);
        if(fromAccount != null) {
            fromAccount.debit(amount);
        }else {
            this.owner.updateNetAssetsAndLiabilities(amount);
        }
        this.owner.updateTotalAssets();
        this.owner.updateTotalLiabilities();
        this.owner.updateNetAsset();
    }

    public List<InstallmentPlan> getInstallmentPlans() {
        return installmentPlans;
    }
    public void addInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.add(installmentPlan);
        currentDebt = currentDebt.add(installmentPlan.getRemainingAmount());
    }
    public void removeInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.remove(installmentPlan);
        currentDebt = currentDebt.subtract(installmentPlan.getRemainingAmount());
    }
    public void repayInstallmentPlan(InstallmentPlan installmentPlan) {
        if (installmentPlans.contains(installmentPlan)) {
            BigDecimal amount = installmentPlan.getMonthlyPayment(installmentPlan.getPaidPeriods() + 1);
            installmentPlan.repayOnePeriod();
            currentDebt = currentDebt.subtract(amount);
            this.owner.updateNetAssetsAndLiabilities(amount);
        } else {
            throw new IllegalArgumentException("Installment plan not found in this account");
        }
    }

    //ritorna il totale delle rate ancora da pagare collegate a questo account
    public BigDecimal getRemainingInstallmentDebt(){
        return installmentPlans.stream()
                .map(InstallmentPlan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
