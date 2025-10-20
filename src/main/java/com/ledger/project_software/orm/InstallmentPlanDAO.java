package com.ledger.project_software.orm;

import com.ledger.project_software.domain.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstallmentPlanDAO extends JpaRepository<InstallmentPlan, Long> {
    @Query("SELECT ip FROM InstallmentPlan ip " +
            "WHERE ip.linkedAccount.id = :accountId")
    List<InstallmentPlan> findByLinkedAccountId(Long accountId);
}
