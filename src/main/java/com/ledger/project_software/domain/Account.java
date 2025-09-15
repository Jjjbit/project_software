package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Account {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(length = 100, nullable = false)
    protected String name= "Default Account";

    @Column(precision = 15, scale = 2)
    protected BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name= "account_type", nullable = false)
    protected AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name= "account_category")
    protected AccountCategory category;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    protected User owner;

    //@JoinColumn(name = "currency_id")
    //@Transient
    //protected Currency currency;

    @Column(length = 500)
    protected String notes;

    @Column(name = "is_hidden", nullable = false)
    protected Boolean hidden=false;

    @OneToMany(mappedBy = "fromAccount", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    protected List<Transaction> outgoingTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "toAccount", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    protected List<Transaction> incomingTransactions = new ArrayList<>();

    @Column(name = "included_in_net_asset", nullable = false)
    protected Boolean includedInNetAsset = true;

    @Column(name= "selectable", nullable = false)
    protected Boolean selectable = true;

    public Account() {}
    public Account(
            String name,
            BigDecimal balance,
            AccountType type,
            AccountCategory category,
            User owner,
            String notes,
            boolean includedInNetAsset,
            boolean selectable) {
        this.name = name;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.type = type;
        this.category = category;
        this.owner = owner;
        //this.currency = currency;
        this.notes = notes;
        this.includedInNetAsset = includedInNetAsset;
        this.selectable = selectable;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
        owner.updateTotalAssets();
        owner.updateNetAsset();
    }
    public abstract void debit(BigDecimal amount);
    public void hide() {
        this.hidden = true;
        this.owner.updateTotalAssets();
        this.owner.updateNetAsset();
        this.owner.updateTotalLiabilities();
    }


    public void setIncludedInNetAsset(boolean includedInNetAsset) {
        this.includedInNetAsset =includedInNetAsset;
    }
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    public void setOwner(User owner) {
        this.owner = owner;
    }
    /*public void setCurrencyCode(Currency currency) {
        this.currency = currency;
    }*/
    public void setId(Long id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public void setBalance(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative.");
        }
        this.balance = balance;
        this.owner.updateTotalAssets();
        this.owner.updateNetAsset();
    }

    public AccountType getType() { return type; }
    public AccountCategory getCategory() {
        return category;
    }
    public String getName() {
        return name;
    }
    public User getOwner() {
        return owner;
    }
    private List<Transaction> getTransactions() {
        List<Transaction> allTransactions = new ArrayList<>();
        allTransactions.addAll(incomingTransactions);
        allTransactions.addAll(outgoingTransactions);
        return allTransactions;
    }
    public List<Transaction> getIncomingTransactions() {
        return incomingTransactions;
    }
    public List<Transaction> getOutgoingTransactions() {
        return outgoingTransactions;
    }
    public BigDecimal getBalance() {
        return this.balance;
    }
    public Long getId() {
        return id;
    }
    public String getNotes() {
        return notes;
    }
    public Boolean getSelectable() {
        return selectable;
    }
    public Boolean getHidden() {
        return hidden;
    }
    public Boolean getIncludedInNetAsset() {
        return includedInNetAsset;
    }
    public void addTransaction(Transaction transaction) { //for test
        if(transaction instanceof Income){
            incomingTransactions.add(transaction);
        } else if (transaction instanceof Expense) {
            outgoingTransactions.add(transaction);
        } else if (transaction instanceof Transfer) {

            if ((transaction.getFromAccount() != null && transaction.getFromAccount().equals(this))) {
                outgoingTransactions.add(transaction);
            }
            if (transaction.getToAccount() != null &&  transaction.getToAccount().equals(this)) {
                incomingTransactions.add(transaction);
            }
        }
        transaction.execute();
    }


    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return getTransactions().stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }

    public BigDecimal getAccountTotalIncomeForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME
                        || (tx.getType() == TransactionType.TRANSFER
                        && tx instanceof Transfer
                        && ((Transfer) tx).getToAccount().equals(this)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal getAccountTotalExpenseForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE
                        || (tx.getType() == TransactionType.TRANSFER
                        && tx instanceof Transfer
                        && ((Transfer) tx).getFromAccount().equals(this)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
