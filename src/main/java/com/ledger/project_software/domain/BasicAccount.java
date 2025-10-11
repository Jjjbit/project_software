package com.ledger.project_software.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "basic_account")
public class BasicAccount extends Account {
    public BasicAccount() {}
    public BasicAccount(
            String name,
            BigDecimal balance,
            String note,
            boolean includedInNetWorth,
            boolean selectable,
            AccountType type,
            AccountCategory category,
            User owner
    ) {
        super(name, balance, type,category, owner, note, includedInNetWorth, selectable);
    }

    @Override
    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

}
