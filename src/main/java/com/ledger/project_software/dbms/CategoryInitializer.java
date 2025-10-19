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
    private CategoryDAO categoryDAO;

    @PostConstruct
    public void init() {
        if (categoryDAO.count() == 0) {

            Category food = new Category("Food", CategoryType.EXPENSE);
            Category transport = new Category("Transport", CategoryType.EXPENSE);
            Category salary = new Category("Salary", CategoryType.INCOME);
            Category freelance = new Category("Freelance", CategoryType.INCOME);
            Category bonus = new Category("Bonus", CategoryType.INCOME);
            Category entertainment = new Category("Entertainment", CategoryType.EXPENSE);
            Category utilities = new Category("Utilities", CategoryType.EXPENSE);
            Category health = new Category("Health", CategoryType.EXPENSE);
            Category education = new Category("Education", CategoryType.EXPENSE);
            Category shopping = new Category("Shopping", CategoryType.EXPENSE);
            Category gifts = new Category("Gifts", CategoryType.EXPENSE);
            Category electronics = new Category("Electronics", CategoryType.EXPENSE);

            categoryDAO.save(food);
            categoryDAO.save(transport);
            categoryDAO.save(salary);
            categoryDAO.save(freelance);
            categoryDAO.save(entertainment);
            categoryDAO.save(utilities);
            categoryDAO.save(health);
            categoryDAO.save(education);
            categoryDAO.save(shopping);
            categoryDAO.save(gifts);
            categoryDAO.save(electronics);
            categoryDAO.save(bonus);

            Category breakfast = new Category("Breakfast", CategoryType.EXPENSE);
            Category lunch = new Category("Lunch", CategoryType.EXPENSE);
            Category dinner = new Category("Dinner", CategoryType.EXPENSE);
            categoryDAO.save(breakfast);
            categoryDAO.save(lunch);
            categoryDAO.save(dinner);
            food.getChildren().add(breakfast);
            food.getChildren().add(lunch);
            food.getChildren().add(dinner);
            breakfast.setParent(food);
            lunch.setParent(food);
            dinner.setParent(food);
            categoryDAO.save(food);

            Category bus = new Category("Bus", CategoryType.EXPENSE);
            Category train = new Category("Train", CategoryType.EXPENSE);
            categoryDAO.save(bus);
            categoryDAO.save(train);
            transport.getChildren().add(bus);
            transport.getChildren().add(train);
            bus.setParent(transport);
            train.setParent(transport);
            categoryDAO.save(transport);




        }
    }
}
