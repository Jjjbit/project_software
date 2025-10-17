package com.ledger.project_software.orm;

import com.ledger.project_software.domain.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstallmentPlanDAO extends JpaRepository<InstallmentPlan, Long> {
}
