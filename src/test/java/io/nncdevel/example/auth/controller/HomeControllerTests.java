package io.nncdevel.example.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Demo Account")))
            .andExpect(content().string(containsString("password")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void homePageShowsUsername() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("testuser")));
    }

    @Test
    @WithMockUser
    void homePageHasProfileLink() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/profile")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void profilePageShowsPasskeyManagement() throws Exception {
        mockMvc.perform(get("/profile"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Passkeys")))
            .andExpect(content().string(containsString("Register")));
    }
}
