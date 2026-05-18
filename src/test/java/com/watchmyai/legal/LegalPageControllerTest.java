package com.watchmyai.legal;

import com.watchmyai.config.LegalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegalPageController.class)
@EnableConfigurationProperties(LegalProperties.class)
@TestPropertySource(properties = "watchmyai.legal.contact-email=support@watchmyai.app")
class LegalPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void privacyPageReturnsPublicHtml() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("WatchMyAI Privacy Policy")))
                .andExpect(content().string(containsString("support@watchmyai.app")));
    }

    @Test
    void termsPageReturnsPublicHtml() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("WatchMyAI Terms of Use")))
                .andExpect(content().string(containsString("Subscriptions")));
    }

    @Test
    void legalPagesSupportHeadRequests() throws Exception {
        mockMvc.perform(head("/privacy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")));

        mockMvc.perform(head("/terms"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")));
    }
}
