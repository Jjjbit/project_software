package com.ledger.project_software.business;

import com.ledger.project_software.Repository.AccountRepository;
import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.Account;
import com.ledger.project_software.domain.PasswordUtils;
import com.ledger.project_software.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<String> register(@RequestParam String username,
                                           @RequestParam String password) {
        if (userRepository.findByUsername(username) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        User user = new User(username, PasswordUtils.hash(password));
        userRepository.save(user);
        return ResponseEntity.ok("Registration successful");
    }


    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username,
                                        @RequestParam String password) {
        User existingUser = userRepository.findByUsername(username);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        if (!PasswordUtils.verify(password, existingUser.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password incorrect");
        }
        return ResponseEntity.ok("Login successful");
    }


    @PutMapping("/me")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> updateUserInfo(Principal principal,
                                                 @RequestParam (required = false) String username,
                                                 @RequestParam (required = false) String password) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not logged in");
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        if(username != null){user.setUsername(username);}
        if (password != null && !password.isEmpty()) {
            user.setPassword(PasswordUtils.hash(password));
        }
        userRepository.save(user);
        return ResponseEntity.ok("User info updated");
    }



}