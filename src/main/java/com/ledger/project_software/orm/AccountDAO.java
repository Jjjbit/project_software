package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountDAO extends JpaRepository<Account, Long> {
    Account findByName(String name);

    @Query("SELECT a FROM Account a " +
            "WHERE a.owner.id = :ownerId" +
            " AND a.hidden = false")
    List<Account> findByOwnerId(@Param("ownerId") Long ownerId);
}
