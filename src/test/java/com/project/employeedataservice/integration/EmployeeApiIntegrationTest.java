package com.project.employeedataservice.integration;

import com.example.employeeservice.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring context (controller -> service -> repository -> H2)
 * to confirm the whole create -> retrieve flow behaves correctly end to end,
 * and that the SSN is genuinely never persisted or returned in plaintext.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void createThenGetEmployee_roundTripsCorrectlyWithoutExposingRawSsn() throws Exception {
        String rawSsn = "234-56-7890";
        String payload = """
                {
                  "firstName": "Katherine",
                  "lastName": "Johnson",
                  "dateOfBirth": "1988-08-26",
                  "gender": "FEMALE",
                  "socialSecurityNumber": "%s"
                }
                """.formatted(rawSsn);

        String createBody = mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ssnMasked").value("***-**-7890"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(createBody).doesNotContain(rawSsn);

        // The row actually persisted to the database must not contain the raw SSN either.
        assertThat(employeeRepository.findAll())
                .anySatisfy(employee -> {
                    assertThat(employee.getSsnEncrypted()).doesNotContain(rawSsn);
                    assertThat(employee.getSsnHash()).doesNotContain(rawSsn);
                });

        String id = com.jayway.jsonpath.JsonPath.read(createBody, "$.id");

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Katherine"))
                .andExpect(jsonPath("$.ssnMasked").value("***-**-7890"));
    }

    @Test
    void creatingEmployeeWithDuplicateSsn_returnsConflict() throws Exception {
        String payload = """
                {
                  "firstName": "Dorothy",
                  "lastName": "Vaughan",
                  "dateOfBirth": "1970-03-14",
                  "gender": "FEMALE",
                  "socialSecurityNumber": "345-67-8901"
                }
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void gettingUnknownEmployee_returns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/employees/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
