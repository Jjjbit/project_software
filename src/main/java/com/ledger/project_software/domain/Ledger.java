package com.ledger.project_software.domain;

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
    private User owner;

    @OneToMany(mappedBy = "ledger", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, orphanRemoval = true)
    private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è composizione

    @OneToMany(mappedBy = "ledger", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, orphanRemoval = true)
    private List<LedgerCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "ledger", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, orphanRemoval = true)
    private List<BorrowingAndLending> borrowingAndLendings = new ArrayList<>(); // relazione tra LoanRecord e Ledger è aggregazione

    @Column(name = "total_income", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalIncome = BigDecimal.ZERO; // Totale entrate del ledger

    @Column(name = "total_expenses", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpenses = BigDecimal.ZERO; // Totale uscite del ledger

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName(){return this.name;}
    public User getOwner(){return this.owner;}
    public void addTransaction(Transaction tx) {
        transactions.add(tx); // Aggiunge una transazione al ledger
    }
    public List<Transaction> getTransactions() {
        return transactions;
    }
    public List<LedgerCategory> getCategories(){return categories;}

    public void removeTransaction(Transaction tx) {
        transactions.remove(tx); // Rimuove una transazione dal ledger
    }
    public Long getId() {
        return id;
    }


    public void addCategoryComponent(LedgerCategory category) {
        categories.add(category);
        category.setLedger(this);
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
