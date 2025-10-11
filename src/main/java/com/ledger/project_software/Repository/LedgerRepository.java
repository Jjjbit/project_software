package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {
    Ledger findByName(String name);
    List<Ledger> findByOwner(User owner);
}
