package com.ledger.project_software.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense")
public class Expense extends Transaction {

    public Expense() {}
    public Expense(LocalDate date,
                   BigDecimal amount,
                   String description,
                   Account account,
                   Ledger ledger,
                   LedgerCategory category) {
        super(date, amount, description, account, null, ledger, category, TransactionType.EXPENSE);
    }


}
