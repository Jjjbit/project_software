package com.ledger.project_software.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(name= "date", nullable = false)
    protected LocalDate date;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    protected BigDecimal amount;

    @Column(name = "note", length = 500)
    protected String note;

    @ManyToOne
    @JoinColumn(name = "from_account_id")
    @JsonIgnoreProperties({"outgoingTransactions", "incomingTransactions"})
    protected Account fromAccount; //relaizone tra Transaction e Account è associazione. più transazioni->un account

    @ManyToOne
    @JoinColumn(name = "to_account_id")
    @JsonIgnoreProperties({"outgoingTransactions", "incomingTransactions"})
    protected Account toAccount; //per i trasferimenti tra conti

    @ManyToOne
    @JoinColumn(name = "ledger_id")
    @JsonBackReference("ledger-transactions")
    protected Ledger ledger; //relazione tra Transaction e Ledger è aggregazione. più transazioni -> un ledger

    @ManyToOne
    @JoinColumn(name = "category_id")
    @JsonBackReference("category-transactions")
    protected LedgerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    protected TransactionType type;

    public Transaction() {}
    public Transaction(LocalDate date,
                       BigDecimal amount,
                       String description,
                       Account fromAccount,
                       Account toAccount,
                       Ledger ledger,
                       LedgerCategory category,
                       TransactionType type) {
        this.date = date != null ? date : LocalDate.now();
        this.amount = amount;
        this.note = description;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.ledger = ledger;
        this.category = category;
        this.type = type;
    }
    public LedgerCategory getCategory() {
        return category;
    }
    public Long getId() {
        return id;
    }
    public TransactionType getType() {
        return type;
    }
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getNote() {
        return note;
    }
    public Account getFromAccount() {
        return fromAccount;
    }
    public Account getToAccount() {
        return toAccount;
    }
    public Ledger getLedger() {
        return ledger;
    }
    public void setAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        this.amount = amount;
    }
    public void setDate(LocalDate date) {
        if (date == null) {
            this.date = LocalDate.now();
        } else {
            this.date = date;
        }
    }
    public void setType(TransactionType type) {
        this.type = type;
    }
    public void setNote(String note) {
        this.note = note;
    }
    public void setCategory(LedgerCategory category) {
        this.category = category;
    }
    public void setFromAccount(Account account) {
        this.fromAccount = account;
    }
    public void setToAccount(Account toAccount) {
        this.toAccount = toAccount;
    }
    public void setLedger(Ledger ledger){
        this.ledger = ledger;
    }
}
