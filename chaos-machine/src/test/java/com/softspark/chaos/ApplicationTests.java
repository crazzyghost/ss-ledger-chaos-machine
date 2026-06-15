package com.softspark.chaos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify the application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
