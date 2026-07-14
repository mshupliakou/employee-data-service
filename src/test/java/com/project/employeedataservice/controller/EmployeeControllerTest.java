package com.project.employeedataservice.controller;

import com.project.employeedataservice.dto.EmployeeResponse;
import com.project.employeedataservice.entity.Gender;
import com.project.employeedataservice.exception.EmployeeNotFoundException;
import com.project.employeedataservice.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void createEmployee_withValidPayload_returns201AndMaskedSsn() throws Exception {
        UUID id = UUID.randomUUID();
        EmployeeResponse response = new EmployeeResponse(
                id, "Ada", "Lovelace", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "***-**-6789", Instant.now());

        when(employeeService.createEmployee(any())).thenReturn(response);

        String payload = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "dateOfBirth": "1990-01-01",
                  "gender": "FEMALE",
                  "socialSecurityNumber": "123-45-6789"
                }
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ssnMasked").value("***-**-6789"));
    }

    @Test
    void createEmployee_withMissingFields_returns400WithFieldErrors() throws Exception {
        String payload = """
                {
                  "lastName": "Lovelace",
                  "gender": "FEMALE"
                }
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists())
                .andExpect(jsonPath("$.fieldErrors.socialSecurityNumber").exists());
    }

    @Test
    void createEmployee_withInvalidSsnFormat_returns400() throws Exception {
        String payload = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "dateOfBirth": "1990-01-01",
                  "gender": "FEMALE",
                  "socialSecurityNumber": "not-an-ssn"
                }
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.socialSecurityNumber").exists());
    }

    @Test
    void getEmployee_whenFound_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        EmployeeResponse response = new EmployeeResponse(
                id, "Grace", "Hopper", LocalDate.of(1985, 5, 5),
                Gender.FEMALE, "***-**-4321", Instant.now());

        when(employeeService.getEmployee(eq(id))).thenReturn(response);

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"));
    }

    @Test
    void getEmployee_whenMissing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.getEmployee(eq(id))).thenThrow(new EmployeeNotFoundException(id));

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
