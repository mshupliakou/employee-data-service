package com.project.employeedataservice.service;


import com.project.employeedataservice.dto.EmployeeCreateRequest;
import com.project.employeedataservice.dto.EmployeeResponse;
import com.project.employeedataservice.entity.Employee;
import com.project.employeedataservice.entity.Gender;
import com.project.employeedataservice.exception.DuplicateEmployeeException;
import com.project.employeedataservice.exception.EmployeeNotFoundException;
import com.project.employeedataservice.repository.EmployeeRepository;
import com.project.employeedataservice.security.SsnCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final String TEST_ENC_KEY = "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcyExMTE=";
    private static final String TEST_HMAC_SECRET = "dGVzdC1obWFjLXNlY3JldC1rZXktZm9yLXVuaXQtdGVzdHM=";

    @Mock
    private EmployeeRepository employeeRepository;

    private SsnCryptoService ssnCryptoService;
    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        // A real crypto service (not a mock) is used so that encryption/hash
        // behavior stays realistic; only the repository (i.e. the database) is mocked.
        ssnCryptoService = new SsnCryptoService(TEST_ENC_KEY, TEST_HMAC_SECRET);
        employeeService = new EmployeeService(employeeRepository, ssnCryptoService);
    }

    private EmployeeCreateRequest sampleRequest() {
        return new EmployeeCreateRequest(
                "Ada",
                "Lovelace",
                LocalDate.of(1990, 1, 1),
                Gender.FEMALE,
                "123-45-6789"
        );
    }

    @Test
    void createEmployee_persistsEncryptedSsnAndNeverReturnsPlaintext() {
        when(employeeRepository.existsBySsnHash(any())).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            toSave.setCreatedAt(Instant.now());
            toSave.setUpdatedAt(Instant.now());
            return toSave;
        });

        EmployeeResponse response = employeeService.createEmployee(sampleRequest());

        assertThat(response.id()).isNotNull();
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.ssnMasked()).isEqualTo("***-**-6789");
        assertThat(response.ssnMasked()).doesNotContain("123-45-6789");

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();

        assertThat(saved.getSsnEncrypted()).doesNotContain("123-45-6789");
        assertThat(ssnCryptoService.decrypt(saved.getSsnEncrypted())).isEqualTo("123-45-6789");
        assertThat(saved.getSsnHash()).isEqualTo(ssnCryptoService.hash("123-45-6789"));
    }

    @Test
    void createEmployee_rejectsDuplicateSsn() {
        when(employeeRepository.existsBySsnHash(any())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(sampleRequest()))
                .isInstanceOf(DuplicateEmployeeException.class);

        verify(employeeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getEmployee_returnsMappedResponseWhenFound() {
        UUID id = UUID.randomUUID();
        Employee employee = Employee.builder()
                .id(id)
                .firstName("Grace")
                .lastName("Hopper")
                .dateOfBirth(LocalDate.of(1985, 5, 5))
                .gender(Gender.FEMALE)
                .ssnEncrypted(ssnCryptoService.encrypt("987-65-4321"))
                .ssnHash(ssnCryptoService.hash("987-65-4321"))
                .ssnLastFour("4321")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.getEmployee(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.ssnMasked()).isEqualTo("***-**-4321");
    }

    @Test
    void getEmployee_throwsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployee(id))
                .isInstanceOf(EmployeeNotFoundException.class);
    }
}
