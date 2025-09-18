package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.LedgerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerCategoryRepository extends JpaRepository<LedgerCategory, Long> {
    LedgerCategory findByLedgerAndName(Ledger ledger, String name);
    boolean existsByLedgerAndName(Ledger ledger, String name);
}
