package com.ledger.project_software.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
    @JsonIgnoreProperties({"children"})
    private LedgerCategory parent;

    @Column(length = 20, nullable = false)
    protected CategoryType type;

    @ManyToOne
    @JoinColumn(name = "ledger_id", nullable = false)
    @JsonIgnoreProperties({"transactions", "categories"})
    protected Ledger ledger;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"parent"})
    private List<LedgerCategory> children = new ArrayList<>();


    @OneToMany(mappedBy = "category", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JsonManagedReference("category-transactions")
    private List<Transaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("category-budgets")
    private List<Budget> budgets = new ArrayList<>();

    public LedgerCategory() {}
    public LedgerCategory(String name, CategoryType type, Ledger ledger) {
        this.ledger = ledger;
        this.type = type;
        this.name = name;
    }



    // --- Getter/Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
        return transactions;
    }
}
