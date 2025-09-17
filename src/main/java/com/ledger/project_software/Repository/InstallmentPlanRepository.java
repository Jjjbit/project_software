package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {
}
