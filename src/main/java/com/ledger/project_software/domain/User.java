package com.ledger.project_software.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER,
        ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @OneToMany(mappedBy = "owner",cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Ledger> ledgers= new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Account> accounts= new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    public List<Budget> budgets= new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    public List<BorrowingAndLending> bAndL= new ArrayList<>();

    @Column(name = "total_assets", precision = 15, scale = 2, nullable = true)
    public BigDecimal totalAssets;

    @Column(name = "total_liabilities", precision = 15, scale = 2, nullable = true)
    public BigDecimal totalLiabilities;

    @Column(name = "net_assets", precision = 15, scale = 2, nullable = true)
    public BigDecimal netAssets;

    public User (){}
    public User(String username, String password, Role role){
        this.username = username;
        this.password = password;
        this.role = role;
        if (role == Role.USER) {
            createLedger("Default Ledger");
        }
        this.totalAssets = calculateTotalAssets();
        this.totalLiabilities = calculateTotalLiabilities();
        this.netAssets = calculateNetAssets();
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isRegularUser() {
        return this.role == Role.USER;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getId() {
        return id;
    }
    public void createLedger(String name) {
        Ledger ledger = new Ledger(name, this);
        ledgers.add(ledger);
    }
    public List<Ledger> getLedgers() {
        return ledgers;
    }
    public List<Account> getAccounts() {
        return accounts;
    }
    public List<Budget> getBudgets() {
        return budgets;
    }
    public Role getRole(){return role;}
    public String getPassword(){return password;}
    public String getUsername(){return username;}
    public void setBudget(BigDecimal amount, Budget.Period p, LedgerCategory c) {
        budgets.add(new Budget(amount, p, c,this));
    }
    public void setTotalAssets(BigDecimal totalAssets) {
        this.totalAssets = totalAssets;
    }
    public void setTotalLiabilities(BigDecimal totalLiabilities) {
        this.totalLiabilities = totalLiabilities;
    }
    public void setNetAssets(BigDecimal netAssets) {
        this.netAssets = netAssets;
    }

    public void addAccount(Account account) {
        accounts.add(account);
        account.setOwner(this);
        this.totalAssets = calculateTotalAssets();
        this.totalLiabilities = calculateTotalLiabilities();
        this.netAssets = calculateNetAssets();
    }
    public void addBorrowingAndLending(BorrowingAndLending record) {
        bAndL.add(record);
    }
    public BigDecimal getTotalLiabilities() {
        return totalLiabilities;
    }
    public BigDecimal getTotalAssets() {
        return totalAssets;
    }
    public BigDecimal getNetAssets() {
        return netAssets;
    }

    public BigDecimal calculateTotalLending(){
        return bAndL.stream()
                .filter(record -> !record.isIncoming())
                .filter(record -> record.includedInNetWorth)
                .filter(record -> !record.isEnded)
                .map(BorrowingAndLending::getRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalBorrowing() {
        return bAndL.stream()
                .filter(BorrowingAndLending::isIncoming)
                .filter(record -> record.includedInNetWorth)
                .filter(record -> !record.isEnded)
                .map(BorrowingAndLending::getRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalAssets() {
        BigDecimal totalBalance = accounts.stream()
                .filter(account -> !account.getType().equals(AccountType.LOAN))
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalBalance.add(calculateTotalLending());
    }

    public BigDecimal calculateNetAssets() {
        return calculateTotalAssets().subtract(calculateTotalLiabilities()).add(calculateTotalLending());
    }
    public BigDecimal calculateTotalLiabilities() {
        BigDecimal totalCreditDebt = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof CreditAccount)
                .filter(account-> account.includedInNetAsset && !account.hidden)
                .map(account -> ((CreditAccount) account).getCurrentDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnpaidLoan = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof LoanAccount)
                .filter(account -> account.includedInNetAsset)
                .map(account -> ((LoanAccount) account).getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalCreditDebt.add(calculateTotalBorrowing()).add(totalUnpaidLoan);
    }
    public void updateNetAssetsAndLiabilities(BigDecimal amount) {
        this.totalLiabilities = calculateTotalLiabilities().subtract(amount);
        //this.netAssets = this.netAssets.add(amount);
        this.netAssets = calculateTotalAssets().subtract(this.totalLiabilities);
    }
    public void updateNetAsset(){
        this.netAssets= calculateNetAssets();
    }
    public void updateTotalAssets(){
        this.totalAssets = calculateTotalAssets();
    }
    public void updateTotalLiabilities(){
        this.totalLiabilities = calculateTotalLiabilities();
    }
}
