package io.nncdevel.example.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void unauthenticatedAccessRedirectsToLogin() {
        mockMvc.get().uri("/")
            .assertThat()
            .hasStatus3xxRedirection()
            .redirectedUrl().endsWith("/login");
    }

    @Test
    @WithMockUser
    void authenticatedAccessReturnsOk() {
        mockMvc.get().uri("/")
            .assertThat()
            .hasStatusOk();
    }
}
