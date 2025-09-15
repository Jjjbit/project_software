package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class LedgerCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;


    @ManyToOne
    @JoinColumn(name = "parent_id")
    private LedgerCategory parent;

    @Column(length = 20, nullable = false)
    protected CategoryType type;

    @ManyToOne
    @JoinColumn(name = "ledger_id", nullable = false)
    protected Ledger ledger;


    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerCategory> children = new ArrayList<>();


    @OneToMany(mappedBy = "category", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    private List<Transaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Budget> budgets = new ArrayList<>();

    public LedgerCategory() {}
    public LedgerCategory(String name, CategoryType type, Ledger ledger) {
        this.ledger = ledger;
        this.type = type;
        this.name = name;
    }


    public void addChild(LedgerCategory child) {
        children.add(child);
        child.setParent(this);
    }
    public void removeChild(LedgerCategory child) {
        children.remove(child);
        child.setParent(null);
    }

    public void addTransaction(Transaction tx) {
        transactions.add(tx);
    }

    // --- Getter/Setter ---
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Ledger getLedger() { return ledger; }
    public CategoryType getType() { return type; }
    public void setLedger(Ledger ledger) { this.ledger = ledger; }
    public void setType(CategoryType type) { this.type = type; }

    public LedgerCategory getParent() { return parent; }
    public void setParent(LedgerCategory parent) { this.parent = parent; }
    public List<Budget> getBudgets() { return budgets; }

    public List<LedgerCategory> getChildren() { return children; }
    public List<Transaction> getTransactions() {
        if (parent == null) {
            for (LedgerCategory child : children) {
                this.transactions.addAll(child.getTransactions());
            }
        }
        return transactions;
    }
}
