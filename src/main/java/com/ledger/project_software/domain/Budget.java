package com.ledger.project_software.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
public class Budget {

    public enum Period {
        MONTHLY, YEARLY
    }

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount=BigDecimal.ZERO; // Budget amount

    @Column(name = "period", nullable = false)
    @Enumerated(EnumType.STRING)
    private Period period; // e.g., "monthly", "yearly"

    @ManyToOne
    @JoinColumn(name = "category_id")
    @JsonBackReference("category-budgets")
    private LedgerCategory category; // Category or subcategory

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonBackReference("user-budgets")
    private User owner; // User ID or name

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    public Budget(){}
    public Budget(BigDecimal amount, Period period, LedgerCategory category, User owner) {
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.owner = owner;
        this.startDate = getStartDateForPeriod(LocalDate.now(), this.period);
        this.endDate = getEndDateForPeriod(this.startDate, this.period);
    }
    public static LocalDate getStartDateForPeriod(LocalDate today, Period budgetPeriod) {
        return switch (budgetPeriod) {
            case YEARLY -> LocalDate.of(today.getYear(), 1, 1);
            case MONTHLY -> LocalDate.of(today.getYear(), today.getMonth(), 1);
        };
    }
    public static LocalDate getEndDateForPeriod(LocalDate startDate, Period period) {
        return switch (period) {
            case YEARLY -> LocalDate.of(startDate.getYear(), 12, 31);
            case MONTHLY -> YearMonth.from(startDate).atEndOfMonth();
        };
    }


    public void setCategory(LedgerCategory category) {
        this.category = category;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public LedgerCategory getCategory() {
        return category;
    }
    public Period getPeriod() {
        return period;
    }
    public User getOwner() {
        return owner;
    }
    public void setOwner(User owner) {
        this.owner = owner;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }


    public boolean isActive(LocalDate date) {
        return switch (period) {
            case MONTHLY -> date.isBefore(startDate.plusMonths(1));
            case YEARLY -> date.isBefore(startDate.plusYears(1));
        };
    }

}
