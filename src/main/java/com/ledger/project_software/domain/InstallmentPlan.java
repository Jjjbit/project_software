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

    @Column(name = "remaining_amount", precision = 15, scale = 2)
    private BigDecimal remainingAmount;

    public InstallmentPlan() {}
    public InstallmentPlan(BigDecimal totalAmount,
                           int totalPeriods,
                           BigDecimal feeRate,
                           int paidPeriods,
                           FeeStrategy feeStrategy,
                           Account linkedAccount) {
        this.totalAmount = totalAmount;
        this.totalPeriods = totalPeriods;
        this.feeRate = feeRate;
        this.paidPeriods = paidPeriods;
        this.feeStrategy = feeStrategy;
        this.linkedAccount = linkedAccount;
        this.remainingAmount = getRemainingAmountWithRepaidPeriods();
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }
    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
    public Account getLinkedAccount() {
        return linkedAccount;
    }
    public Long getId() {
        return id;
    }
    public void setLinkedAccount(Account linkedAccount) {
        this.linkedAccount = linkedAccount;
    }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    public void setTotalPeriods(int totalPeriods) {
        this.totalPeriods = totalPeriods;
    }
    public int getPaidPeriods() {
        return paidPeriods;
    }
    public void setPaidPeriods(int paidPeriods) {
        this.paidPeriods = paidPeriods;
    }
    public void setFeeRate(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }
    public void setFeeStrategy(FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    public int getTotalPeriods() {
        return totalPeriods;
    }
    public BigDecimal getFeeRate() {
        return feeRate;
    }
    public FeeStrategy getFeeStrategy() {
        return feeStrategy;
    }
    public BigDecimal getMonthlyPayment(int period) {
        BigDecimal base = totalAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //base amount per period
        BigDecimal fee = totalAmount.multiply(feeRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP); //total fee for the installment

        switch(this.feeStrategy){
            case EVENLY_SPLIT:
                return (totalAmount.add(fee)).divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //(totalAmount+fee)/totalPeriods
            case UPFRONT:
                if(period == 1) {
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
        BigDecimal fee = totalAmount.multiply(feeRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP); //total fee for the installment
        return totalAmount.add(fee).setScale(2, RoundingMode.HALF_UP); //total amount + total fee
    }

    public void repayOnePeriod() {
        BigDecimal monthlyPayment = getMonthlyPayment(paidPeriods + 1);
        remainingAmount = remainingAmount.subtract(monthlyPayment).setScale(2, RoundingMode.HALF_UP);
        paidPeriods++;
    }
    public void repayPartial(BigDecimal amount) {
        //how many monthly payments does this cover?
        BigDecimal paidAmount = BigDecimal.ZERO;
        int periods = 0;
        for(int i = paidPeriods + 1; i <= totalPeriods; i++) {
            BigDecimal monthlyRepayment = getMonthlyPayment(i);
            if(paidAmount.add(monthlyRepayment).compareTo(amount) <= 0) {
                paidAmount = paidAmount.add(monthlyRepayment);
                periods++;
            } else {
                break;
            }
        }
        paidPeriods += periods;
        remainingAmount=remainingAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getRemainingAmountWithRepaidPeriods() {//dipende da paidPeriods
        BigDecimal total = BigDecimal.ZERO;
        for (int i = paidPeriods + 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyPayment(i));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
