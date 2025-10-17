package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.LedgerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerCategoryDAO extends JpaRepository<LedgerCategory, Long> {
    LedgerCategory findByLedgerAndName(Ledger ledger, String name);
    boolean existsByLedgerAndName(Ledger ledger, String name);
    List<LedgerCategory> findByParentId(Long categoryId);
    List<LedgerCategory> findByLedgerIdAndParentIsNull(Long ledgerId);
}
