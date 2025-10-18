package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerDAO extends JpaRepository<Ledger, Long> {
    Ledger findByName(String name);

    @Query("SELECT l FROM Ledger l WHERE l.owner= :owner")
    List<Ledger> findByOwner(@Param ("owner") User owner);

    Ledger findByNameAndOwner(String name, User owner);
}
