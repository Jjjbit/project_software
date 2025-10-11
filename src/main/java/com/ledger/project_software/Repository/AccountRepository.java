package com.ledger.project_software.Repository;

import com.ledger.project_software.domain.Account;
import com.ledger.project_software.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account findByName(String name);
    List<Account> findByOwner(User owner);
}
