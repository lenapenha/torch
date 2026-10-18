package dev.hpsoftware.torch.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc // calls controllers without a real HTTP server
@ActiveProfiles("test")
public class BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // clean before (known starting state) and after (leave torch_test empty for other test classes)
    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE " +
                "stock_movement, product RESTART IDENTITY CASCADE ");
    }

}
