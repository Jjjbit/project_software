package com.ledger.project_software.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
public class Ledger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length =50, nullable= false, unique = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    @JsonBackReference("user-ledgers")
    private User owner;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è composizione

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerCategory> categories = new ArrayList<>();

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName(){return this.name;}
    public void setName(String name){this.name=name;}
    public User getOwner(){return this.owner;}
    public List<Transaction> getTransactions() {
        return transactions;
    }
    public List<LedgerCategory> getCategories(){return categories;}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }


    //does not matter income or expense
    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return transactions.stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .toList();
    }
    public List<Transaction> getTransactionsForYear(int year) {
        return transactions.stream()
                .filter(tx -> tx.getDate().getYear() == year)
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .toList();
    }

    public BigDecimal getTotalIncomeForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

    }
    public BigDecimal getTotalExpenseForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx ->tx.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingForMonth(YearMonth month) {
        BigDecimal totalIncome = getTotalIncomeForMonth(month);
        BigDecimal totalExpense = getTotalExpenseForMonth(month);
        return totalIncome.subtract(totalExpense);

    }
    public BigDecimal getTotalIncomeForYear(int year) {
        return getTransactionsForYear(year).stream()
                .filter(tx ->  tx.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal getTotalExpenseForYear(int year) {
        return getTransactionsForYear(year).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingForYear(int year) {
        BigDecimal totalIncome = getTotalIncomeForYear(year);
        BigDecimal totalExpense = getTotalExpenseForYear(year);
        return totalIncome.subtract(totalExpense);
    }

}
