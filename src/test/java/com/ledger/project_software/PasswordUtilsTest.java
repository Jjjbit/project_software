package com.ledger.project_software;

import com.ledger.project_software.domain.PasswordUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PasswordUtilsTest {

    @Test
    public void testHashAndVerify_WorkTogether() {
        String plain = "password123";
        String hashed = PasswordUtils.hash(plain);

        assertTrue(PasswordUtils.verify(plain, hashed));
        assertFalse(PasswordUtils.verify("wrongPassword", hashed));
    }
}
