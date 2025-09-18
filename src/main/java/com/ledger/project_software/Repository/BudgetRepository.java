package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NULL " +
            "AND :today >= b.startDate " +
            "AND :today <= b.endDate")
    Optional<Budget> findActiveUserBudget(@Param("userId") Long userId,
                                          @Param("today") LocalDate today);


    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NOT NULL " +
            "AND b.category.parent IS NULL " +
            "AND :today >= b.startDate " +
            "AND :today <= b.endDate")
    List<Budget> findActiveCategoriesBudgetByUserId(@Param("userId") Long userId, //ritorna una lista di budget di categorie di user
                                                    @Param("today") LocalDate today);


    @Query("SELECT b FROM Budget b " +
            "WHERE b.owner.id = :userId " +
            "AND b.category IS NOT NULL " +
            "AND b.category.parent IS NOT NULL " +
            "AND :today >= b.startDate " +
            "AND :today <= b.endDate")
    Optional<Budget> findActiveSubCategoryBudget(@Param("userId") Long userId, //ritorna il budget di una sottocategoria
                                                 @Param("today") LocalDate today);
}
