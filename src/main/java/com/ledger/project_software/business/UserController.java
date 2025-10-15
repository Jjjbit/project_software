package com.ledger.project_software.business;

import com.ledger.project_software.Repository.UserRepository;
import com.ledger.project_software.domain.Ledger;
import com.ledger.project_software.domain.PasswordUtils;
import com.ledger.project_software.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<String> register(@RequestParam String username,
                                           @RequestParam String password) {
        if(username == null || username.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username cannot be empty");
        }
        if (userRepository.findByUsername(username) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        if(password == null || password.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password cannot be empty");
        }
        if(password.length() < 6){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password must be at least 6 characters long");
        }
        if(username.length() < 3 || username.length() > 20){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username must be between 3 and 20 characters long");
        }
        User user = new User(username, PasswordUtils.hash(password));
        Ledger defaultLedger = new Ledger("Default Ledger", user);
        user.getLedgers().add(defaultLedger);
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
        if(username != null && !username.isEmpty()){user.setUsername(username);}
        if (password != null && !password.isEmpty()) {
            user.setPassword(PasswordUtils.hash(password));
        }
        userRepository.save(user);
        return ResponseEntity.ok("User info updated");
    }

    @GetMapping("/my-assets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getUserAssets(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalAssets", user.getTotalAssets());
        response.put("totalLiabilities", user.getTotalLiabilities());
        response.put("netAssets", user.getNetAssets());
        response.put("totalLending", user.getTotalLending());
        response.put("totalBorrowing", user.getTotalBorrowing());

        return ResponseEntity.ok(response);
    }



}