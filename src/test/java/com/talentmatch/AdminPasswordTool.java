
package com.talentmatch;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AdminPasswordTool {

    public static void main(String[] args) {
        String password = System.getenv("TalentMatch@Test2026!");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Chưa nhập ADMIN_RESET_PASSWORD"
            );
        }

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        System.out.println(encoder.encode(password));
    }
}
