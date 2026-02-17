package com.xxl.job.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests in xxl-job-admin.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Spring Boot test context with full application setup
 *   <li>TestContainers MySQL database for integration tests
 *   <li>Test profile activation
 *   <li>Dynamic datasource configuration
 * </ul>
 *
 * <p>Usage: Extend this class for integration tests that require database access.
 *
 * <pre>{@code
 * class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     @Resource
 *     private MyService service;
 *
 *     @Test
 *     void testDatabaseOperation() {
 *         // test code with real database
 *     }
 * }
 * }</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    protected static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("xxl_job_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("tables_xxl_job.sql");

    /**
     * Dynamically configure datasource properties from TestContainers MySQL instance.
     *
     * @param registry the dynamic property registry
     */
    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    public void setUp() {
        // Common setup for all integration tests
        // Override in subclasses if needed
    }

    @AfterEach
    public void tearDown() {
        // Common cleanup for all integration tests
        // Override in subclasses if needed
    }
}
