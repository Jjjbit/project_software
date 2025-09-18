package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger IN (SELECT l FROM Ledger l WHERE l.owner.id = :userId) " +
            "AND t.type = 'EXPENSE' " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumExpensesByUserAndPeriod(@Param("userId") Long userId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);


    //expense for single category
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


    //expense for category and its subcategories
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.ledger.owner.id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.category.id IN :categoryIds " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumExpensesByCategoryAndPeriod(@Param("userId") Long userId,
                                              @Param("categoryIds") List<Long> categoryIds,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
