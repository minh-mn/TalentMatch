
package com.talentmatch;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CheckAdminPassword {

    public static void main(String[] args) {

        String password = System.getenv("TalentMatch@Test2026!");
        String hash = System.getenv("$2a$");

        if (password == null || hash == null) {
            throw new IllegalStateException(
                    "Thiếu ADMIN_TEST_PASSWORD hoặc ADMIN_TEST_HASH"
            );
        }

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        boolean matches = encoder.matches(password, hash);

        System.out.println("Mat khau khop: " + matches);
    }
}
