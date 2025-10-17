package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetDAO extends JpaRepository<Budget, Long> {
    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NULL " +
            "AND :today >= b.startDate " +
            "AND :today <= b.endDate")
    Optional<Budget> findActiveUncategorizedBudgetByUserId(@Param("userId") Long userId,
                                                           @Param("today") LocalDate today);


    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NOT NULL " +
            "AND b.category.parent IS NULL " +
            "AND :today BETWEEN b.startDate AND b.endDate")
    List<Budget> findActiveCategoriesBudgetByUserId(@Param("userId") Long userId, //ritorna una lista di budget di categorie di user
                                                    @Param("today") LocalDate today);


    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NOT NULL " +
            "AND b.category.parent IS NOT NULL " +
            "AND b.category.id = :subCategoryId " +
            "AND b.period = :period " +
            "AND :today BETWEEN b.startDate AND b.endDate")
    Optional<Budget> findActiveSubCategoryBudget(@Param("userId") Long userId, //ritorna il budget di una sottocategoria
                                                 @Param("subCategoryId") Long subCategoryId,
                                                 @Param("today") LocalDate today,
                                                 @Param("period") Budget.Period period);

    //for test
    @Query("SELECT b FROM Budget b " +
       "WHERE b.owner.id = :userId " +
       "AND (:categoryId IS NULL AND b.category IS NULL OR :categoryId IS NOT NULL AND b.category.id = :categoryId) " +
       "AND b.period = :period")
    Optional<Budget> findByUserAndOptionalCategoryAndPeriod(@Param("userId") Long userId,
                                                            @Param("categoryId") Long categoryId,
                                                            @Param("period") Budget.Period period);


    //ritorna il budget attivo di una categoria
    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NOT NULL " +
            "AND b.category.parent IS NULL " +
            "AND b.category.id = :categoryId " +
            "AND b.period = :period " +
            "AND :today BETWEEN b.startDate AND b.endDate")
    Optional<Budget> findActiveCategoryBudget(@Param("userId") Long userId,
                                              @Param("categoryId") Long categoryId,
                                              @Param("today") LocalDate today,
                                              @Param("period") Budget.Period period);

}

