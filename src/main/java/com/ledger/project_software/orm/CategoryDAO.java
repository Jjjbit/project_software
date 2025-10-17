package com.ledger.project_software.orm;

import com.ledger.project_software.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryDAO extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNull();
}
