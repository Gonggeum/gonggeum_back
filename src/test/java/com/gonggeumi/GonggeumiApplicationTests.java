package com.gonggeumi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonggeumi.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GonggeumiApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void healthIsPublicAndDoesNotExposeDependencies() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void protectedRequestHasConsistentServerGeneratedRequestId() throws Exception {
        var result = mvc.perform(get("/api/v1/teams").header("X-Request-ID", "untrusted-id"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andReturn();
        String id = result.getResponse().getHeader("X-Request-ID");
        assertThat(java.util.UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("request_id").asText()).isEqualTo(id);
    }

    @Test
    void writesWithoutCsrfAreForbidden() throws Exception {
        mvc.perform(post("/api/v1/teams"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void csrfTokenAloneDoesNotAuthorizeBusinessRequests() throws Exception {
        mvc.perform(post("/api/v1/teams").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jsonUsesContractNamesAndRejectsInvalidInput() throws Exception {
        var response = new ApiResponse<>("ok", new ApiResponse.Meta("test-id", false));
        assertThat(mapper.readTree(mapper.writeValueAsString(response)).at("/meta/request_id").asText()).isEqualTo("test-id");
        for (String json : new String[]{"{\"amount\":1,\"role\":\"ADMIN\"}", "{\"amount\":1.5}", "{\"amount\":\"1\"}"}) {
            assertThatThrownBy(() -> mapper.readValue(json, Amount.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
    }
    record Amount(long amount) {}
}
