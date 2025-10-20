package com.ledger.project_software.business;

import com.ledger.project_software.domain.Account;
import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.PasswordUtils;
import com.ledger.project_software.domain.User;
import com.ledger.project_software.orm.AccountDAO;
import com.ledger.project_software.orm.LedgerDAO;
import com.ledger.project_software.orm.UserDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class UserService {
    private final UserDAO userDAO;
    private final AccountDAO accountDAO;
    private final LedgerDAO ledgerDAO;
    private final LedgerService ledgerService;

    public UserService(UserDAO userDAO, AccountDAO accountDAO, LedgerDAO ledgerDAO, LedgerService ledgerService) {
        this.userDAO = userDAO;
        this.accountDAO = accountDAO;
        this.ledgerDAO = ledgerDAO;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public User register(String username, String password) {
        if(username == null || username.isEmpty()){
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (userDAO.findByUsername(username) != null) {
            throw new IllegalArgumentException("Username already exists");
        }
        if(password == null || password.isEmpty()){
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if(password.length() < 6){
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if(username.length() < 3 || username.length() > 20){
            throw new IllegalArgumentException("Username must be between 3 and 20 characters long");
        }
        User user = new User(username, PasswordUtils.hash(password));
        userDAO.save(user);
        ledgerService.createLedger("Default Ledger", user);
        return user;
    }

    public String login(String username, String password) {
        User existingUser = userDAO.findByUsername(username);
        if (existingUser == null) {
            throw new IllegalArgumentException("User does not exist");
        }
        if (!PasswordUtils.verify(password, existingUser.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }
        return "Login successful";
    }
    @Transactional
    public void updateUserInfo(User user, String username,String password) {
        validateUser(user);
        if(username != null && !username.isEmpty()){
            if(userDAO.findByUsername(username) != null){
                throw new IllegalArgumentException("Username already exists");

            }
            user.setUsername(username);}
        if (password != null && !password.isEmpty()) {
            user.setPassword(PasswordUtils.hash(password));
        }
        userDAO.save(user);
    }

    public Map<String, Object> getUserAssets(User user) {
        validateUser(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalAssets", user.getTotalAssets());
        response.put("totalLiabilities", user.getTotalLiabilities());
        response.put("netAssets", user.getNetAssets());
        response.put("totalLending", user.getTotalLending());
        response.put("totalBorrowing", user.getTotalBorrowing());

        return response;
    }
    public List<Ledger> getUserLedgers(User user) {
        validateUser(user);
        //return user.getLedgers();
        return ledgerDAO.findByOwner(user);
    }
    public List<Account> getUserAccounts(User user) {
        validateUser(user);
        //return user.getAccounts();
        return accountDAO.findByOwnerId(user.getId());
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if(userDAO.findByUsername(user.getUsername()) == null){
            throw new IllegalArgumentException("User does not exist");
        }
    }

}
