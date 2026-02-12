package com.xxl.job.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for Spring MVC controller integration tests.
 *
 * <p>Provides MockMvc setup with full Spring context initialization. Subclasses inherit a
 * configured MockMvc instance for testing HTTP endpoints.
 *
 * <p>Uses random port to avoid conflicts when running tests in parallel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractSpringMvcTest {

    @Autowired private WebApplicationContext applicationContext;

    protected MockMvc mockMvc;

    /**
     * Initializes MockMvc with the full Spring application context.
     *
     * <p>Runs before each test method to ensure clean state.
     */
    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.applicationContext).build();
    }
}
