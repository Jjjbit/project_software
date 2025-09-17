package com.ledger.project_software.dbms;

import com.ledger.project_software.Repository.CategoryRepository;
import com.ledger.project_software.domain.Category;
import com.ledger.project_software.domain.CategoryType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CategoryInitializer {
    @Autowired
    private CategoryRepository categoryRepository;

    @PostConstruct
    public void init() {
        if (categoryRepository.count() == 0) {

            Category transport = new Category("Transport", CategoryType.EXPENSE);
            Category salary = new Category("Salary", CategoryType.INCOME);
            Category freelance = new Category("Freelance", CategoryType.INCOME);
            Category entertainment = new Category("Entertainment", CategoryType.EXPENSE);
            Category utilities = new Category("Utilities", CategoryType.EXPENSE);
            Category health = new Category("Health", CategoryType.EXPENSE);
            Category education = new Category("Education", CategoryType.EXPENSE);
            Category shopping = new Category("Shopping", CategoryType.EXPENSE);
            Category gifts = new Category("Gifts", CategoryType.EXPENSE);

            categoryRepository.save(transport);
            categoryRepository.save(salary);
            categoryRepository.save(freelance);
            categoryRepository.save(entertainment);
            categoryRepository.save(utilities);
            categoryRepository.save(health);
            categoryRepository.save(education);
            categoryRepository.save(shopping);
            categoryRepository.save(gifts);


            Category bus = new Category("Bus", CategoryType.EXPENSE);
            Category train = new Category("Train", CategoryType.EXPENSE);
            categoryRepository.save(bus);
            categoryRepository.save(train);
            transport.addChild(bus);
            transport.addChild(train);
            categoryRepository.save(transport);




        }
    }
}
