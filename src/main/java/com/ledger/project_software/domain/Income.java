package com.ledger.project_software.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "income")
public class Income extends Transaction {
    public  Income (LocalDate date, BigDecimal amount, String description, Account account, Ledger ledger, LedgerCategory category) {
        super(date, amount, description, null, account, ledger, category, TransactionType.INCOME);
    }

    public Income() {}
}
