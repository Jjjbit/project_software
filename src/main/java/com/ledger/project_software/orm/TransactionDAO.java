package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionDAO extends JpaRepository<Transaction, Long> {
    //total expense for user in period
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger IN (SELECT l FROM Ledger l WHERE l.owner.id = :userId) " +
            "AND t.type = 'EXPENSE' " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumExpensesByUserAndPeriod(@Param("userId") Long userId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);


    //expense for single subcategory
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger.owner.id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.category.id = :categoryId " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumExpensesBySubCategoryAndPeriod(@Param("userId") Long userId,
                                                 @Param("categoryId") Long categoryId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);


    //expense of group of categories
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger.owner.id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.category.id IN :categoryIds " +
            "AND t.date BETWEEN :startDate AND :endDate " )
    BigDecimal sumExpensesByCategoryIdsAndPeriod(@Param("userId") Long userId,
                                                 @Param("categoryIds") List<Long> categoryIds,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.ledger.owner.id = :ownerId " +
            "AND (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) " +
            "AND t.date BETWEEN :start AND :end " +
            "ORDER BY t.date DESC")
    List<Transaction> findByAccountIdAndOwnerId(@Param("accountId") Long accountId,
                                                @Param("ownerId") Long ownerId,
                                                @Param("start") LocalDate start,
                                                @Param("end") LocalDate end);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.ledger.id = :ledgerId " +
            "AND t.ledger.owner.id = :ownerId " +
            "AND t.date BETWEEN :start AND :end " +
            "ORDER BY t.date DESC")
    List<Transaction> findByLedgerIdAndOwnerId(@Param("ledgerId") Long ledgerId,
                                               @Param("ownerId") Long ownerId,
                                               @Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger.id = :ledgerId " +
            "AND t.type = 'INCOME' " +
            "AND t.date BETWEEN :start AND :end")
    BigDecimal sumIncomeByLedgerAndPeriod(@Param("ledgerId") Long ledgerId,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger.id = :ledgerId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.date BETWEEN :start AND :end")
    BigDecimal sumExpenseByLedgerAndPeriod(@Param("ledgerId") Long ledgerId,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.category.id IN :categoryIds " +
            "AND t.date BETWEEN :start AND :end " +
            "ORDER BY t.date DESC")
    List<Transaction> findByCategoryIdsAndUserId(@Param("categoryIds") List<Long> categoryIds,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.category.id = :categoryId " +
            "AND t.date BETWEEN :start AND :end " +
            "ORDER BY t.date DESC")
    List<Transaction> findByCategoryIdAndUserId(@Param("categoryId") Long categoryId,
                                                @Param("start") LocalDate start,
                                                @Param("end") LocalDate end);
}
