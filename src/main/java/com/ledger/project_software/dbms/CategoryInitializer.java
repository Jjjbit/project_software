package com.ledger.project_software.dbms;

import com.ledger.project_software.orm.CategoryDAO;
import com.ledger.project_software.domain.Category;
import com.ledger.project_software.domain.CategoryType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CategoryInitializer {
    @Autowired
    private CategoryDAO categoryRepository;

    @PostConstruct
    public void init() {
        if (categoryRepository.count() == 0) {

            Category food = new Category("Food", CategoryType.EXPENSE);
            Category transport = new Category("Transport", CategoryType.EXPENSE);
            Category salary = new Category("Salary", CategoryType.INCOME);
            Category freelance = new Category("Freelance", CategoryType.INCOME);
            Category entertainment = new Category("Entertainment", CategoryType.EXPENSE);
            Category utilities = new Category("Utilities", CategoryType.EXPENSE);
            Category health = new Category("Health", CategoryType.EXPENSE);
            Category education = new Category("Education", CategoryType.EXPENSE);
            Category shopping = new Category("Shopping", CategoryType.EXPENSE);
            Category gifts = new Category("Gifts", CategoryType.EXPENSE);
            Category electronics = new Category("Electronics", CategoryType.EXPENSE);

            categoryRepository.save(food);
            categoryRepository.save(transport);
            categoryRepository.save(salary);
            categoryRepository.save(freelance);
            categoryRepository.save(entertainment);
            categoryRepository.save(utilities);
            categoryRepository.save(health);
            categoryRepository.save(education);
            categoryRepository.save(shopping);
            categoryRepository.save(gifts);
            categoryRepository.save(electronics);

            Category breakfast = new Category("Breakfast", CategoryType.EXPENSE);
            Category lunch = new Category("Lunch", CategoryType.EXPENSE);
            Category dinner = new Category("Dinner", CategoryType.EXPENSE);
            categoryRepository.save(breakfast);
            categoryRepository.save(lunch);
            categoryRepository.save(dinner);
            food.getChildren().add(breakfast);
            food.getChildren().add(lunch);
            food.getChildren().add(dinner);
            breakfast.setParent(food);
            lunch.setParent(food);
            dinner.setParent(food);
            categoryRepository.save(food);

            Category bus = new Category("Bus", CategoryType.EXPENSE);
            Category train = new Category("Train", CategoryType.EXPENSE);
            categoryRepository.save(bus);
            categoryRepository.save(train);
            transport.getChildren().add(bus);
            transport.getChildren().add(train);
            bus.setParent(transport);
            train.setParent(transport);
            categoryRepository.save(transport);




        }
    }
}
