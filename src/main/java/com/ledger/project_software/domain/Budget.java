package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
    private Period period; // e.g., "monthly", "yearly", "weekly"

    @ManyToOne
    @JoinColumn(name = "category_id")
    private LedgerCategory category; // Category or subcategory

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // User ID or name

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    public Budget(){}
    public Budget(BigDecimal amount, Period period, LedgerCategory category, User owner) {
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.owner = owner;
        this.startDate = getStartDateForPeriod(LocalDate.now(), this.period);
    }
    public static LocalDate getStartDateForPeriod(LocalDate today, Period period) {
        return switch (period) {
            case YEARLY -> LocalDate.of(today.getYear(), 1, 1);
            case MONTHLY -> LocalDate.of(today.getYear(), today.getMonth(), 1);
        };
    }

    public Long getId() {
        return id;
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
    public boolean isForCategory() {
        return category != null;
    }

    public boolean isTransactionInPeriod(Transaction t, Period p) {
        LocalDate txDate = t.getDate();
        return switch (p) {
            case MONTHLY -> txDate.getYear() == startDate.getYear()
                    && txDate.getMonth() == startDate.getMonth();
            case YEARLY -> txDate.getYear() == startDate.getYear();
        };
    }
    public boolean belongsTo(LedgerCategory cc) {
        return category != null && category.equals(cc);
    }

    public boolean isInPeriod(LocalDate date) {
        return switch (period) {
            case MONTHLY -> date.isBefore(startDate.plusMonths(1));
            case YEARLY -> date.isBefore(startDate.plusYears(1));
        };
    }

}
