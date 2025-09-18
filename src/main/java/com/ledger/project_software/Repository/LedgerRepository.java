package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {
    Ledger findByName(String name);
}
