package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class BorrowingAndLending {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // identificatore univoco del prestito o del debito

    @Column(name="name", length = 100, nullable = false)
    protected String name; // nome del prestito o del debito
    @ManyToOne
    @JoinColumn(name = "owner_id")
    protected User owner; // proprietario del prestito o del debito, può essere null

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    protected BigDecimal totalAmount;

    @Column(name = "repaid_amount", precision = 15, scale = 2, nullable = false)
    protected BigDecimal repaidAmount= BigDecimal.ZERO;

    @Column(name = "loan_date", nullable = false)
    protected LocalDate loanDate= LocalDate.now();

    @Column(name = "notes", length = 500)
    protected String notes; //può essere null

    @Column(name = "included_in_net_worth", nullable = false)
    protected boolean includedInNetWorth = true;

    @ManyToOne
    @JoinColumn(name = "ledger_id", nullable = false)
    protected Ledger ledger;

    @Column(name = "is_ended", nullable = false)
    protected boolean isEnded = false;

    public BorrowingAndLending() {}
    public BorrowingAndLending(String name, BigDecimal amount, String notes, User owner, Ledger ledger) {
        this.name = name;
        this.totalAmount = amount;
        this.notes = notes;
        this.owner = owner;
        this.ledger = ledger;
    }
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    public BigDecimal getRepaidAmount() { return repaidAmount; }
    public BigDecimal getRemaining() {
        return totalAmount.subtract(repaidAmount);
    }
    public abstract boolean isIncoming();
    public void setName(String name) {
        this.name = name;
    }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    public void setRepaidAmount(BigDecimal repaidAmount) {
        this.repaidAmount = repaidAmount;
        checkAndUpdateStatus();
    }
    public void setLoanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public void setIncludedInNetWorth(boolean includedInNetWorth) {
        this.includedInNetWorth = includedInNetWorth;
    }
    /*public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }*/

    public void checkAndUpdateStatus() {
        if (repaidAmount.compareTo(totalAmount) >= 0) {
            isEnded = true;
        }
    }

}
