package io.nncdevel.example.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTests {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void loginPageIsAccessibleWithoutAuth() {
        mockMvc.get().uri("/login")
            .assertThat()
            .hasStatusOk()
            .bodyText()
            .contains("Demo Account")
            .contains("password");
    }

    @Test
    @WithMockUser(username = "testuser")
    void homePageShowsUsername() {
        mockMvc.get().uri("/")
            .assertThat()
            .hasStatusOk()
            .bodyText()
            .contains("testuser");
    }

    @Test
    @WithMockUser
    void homePageHasProfileLink() {
        mockMvc.get().uri("/")
            .assertThat()
            .hasStatusOk()
            .bodyText()
            .contains("/profile");
    }

    @Test
    @WithMockUser(username = "testuser")
    void profilePageShowsPasskeyManagement() {
        mockMvc.get().uri("/profile")
            .assertThat()
            .hasStatusOk()
            .bodyText()
            .contains("Passkeys")
            .contains("Register");
    }
}
