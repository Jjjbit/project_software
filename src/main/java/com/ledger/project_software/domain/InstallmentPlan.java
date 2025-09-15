package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class InstallmentPlan {

    public enum FeeStrategy {
        EVENLY_SPLIT,
        UPFRONT,
        FINAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Unique identifier for the installment plan

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "total_periods", nullable = false)
    private int totalPeriods;

    @Column(name = "fee_rate", precision = 5, scale = 4)
    private BigDecimal feeRate;

    @Column(name = "paid_periods")
    private int paidPeriods = 0;

    @Enumerated
    private FeeStrategy feeStrategy = FeeStrategy.EVENLY_SPLIT;

    @ManyToOne
    @JoinColumn(name = "linked_account_id")
    private Account linkedAccount;

    public InstallmentPlan() {}
    public InstallmentPlan(BigDecimal totalAmount, int totalPeriods, BigDecimal feeRate, int paidPeriods, FeeStrategy feeStrategy, Account linkedAccount) {
        this.totalAmount = totalAmount;
        this.totalPeriods = totalPeriods;
        this.feeRate = feeRate;
        this.paidPeriods = paidPeriods;
        this.feeStrategy = feeStrategy;
        this.linkedAccount = linkedAccount;
    }

    public void setLinkedAccount(Account linkedAccount) {
        this.linkedAccount = linkedAccount;
    }
    public int getPaidPeriods() {
        return paidPeriods;
    }
    public BigDecimal getMonthlyPayment(int period) {
        BigDecimal base = totalAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //base amount per period
        BigDecimal fee = totalAmount.multiply(feeRate); //total fee for the installment

        switch(this.feeStrategy){
            case EVENLY_SPLIT:
                return (totalAmount.add(fee)).divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //(totalAmount+fee)/totalPeriods
            case UPFRONT:
                if(period == 0) {
                    return base.add(fee).setScale(2, RoundingMode.HALF_UP); //first payment includes all fees
                } else {
                    return base; //subsequent payments are just the base amount
                }
            case FINAL:
                if (period == totalPeriods){
                    return base.add(fee).setScale(2, RoundingMode.HALF_UP); //last payment includes all fees
                } else {
                    return base; //all other payments are just the base amount
                }
            default:
                throw new IllegalArgumentException("Unknown fee strategy: " + feeStrategy); // For other fee strategies
        }

    }
    public BigDecimal getTotalPayment(){
        BigDecimal fee= totalAmount.multiply(feeRate); //total fee for the installment
        return totalAmount.add(fee).setScale(2, RoundingMode.HALF_UP); //total amount + total fee
    }

    public void repayOnePeriod() {
        if (paidPeriods < totalPeriods) {
            BigDecimal amountToPay = getMonthlyPayment(paidPeriods + 1);
            linkedAccount.debit(amountToPay); // Debit the amount from the linked account
            paidPeriods++;
            linkedAccount.owner.updateNetAssetsAndLiabilities(amountToPay);
        } else {
            throw new IllegalStateException("All periods have already been paid.");
        }
    }
    public BigDecimal getRemainingAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = paidPeriods + 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyPayment(i));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }


    public boolean isCompleted() {
        return paidPeriods >= totalPeriods;
    }
}
