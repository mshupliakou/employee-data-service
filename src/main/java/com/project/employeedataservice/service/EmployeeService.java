package com.project.employeedataservice.service;


import com.project.employeedataservice.dto.EmployeeCreateRequest;
import com.project.employeedataservice.dto.EmployeeResponse;
import com.project.employeedataservice.entity.Employee;
import com.project.employeedataservice.exception.DuplicateEmployeeException;
import com.project.employeedataservice.exception.EmployeeNotFoundException;
import com.project.employeedataservice.repository.EmployeeRepository;
import com.project.employeedataservice.security.SsnCryptoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final SsnCryptoService ssnCryptoService;

    public EmployeeService(EmployeeRepository employeeRepository, SsnCryptoService ssnCryptoService) {
        this.employeeRepository = employeeRepository;
        this.ssnCryptoService = ssnCryptoService;
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeCreateRequest request) {
        String ssnHash = ssnCryptoService.hash(request.socialSecurityNumber());

        if (employeeRepository.existsBySsnHash(ssnHash)) {
            throw new DuplicateEmployeeException("An employee with this social security number already exists");
        }

        String lastFour = ssnCryptoService.lastFourDigits(request.socialSecurityNumber());

        Employee employee = Employee.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .ssnEncrypted(ssnCryptoService.encrypt(request.socialSecurityNumber()))
                .ssnHash(ssnHash)
                .ssnLastFour(lastFour)
                .build();

        Employee saved = employeeRepository.save(employee);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> listEmployees(Pageable pageable) {
        return employeeRepository.findAll(pageable).map(this::toResponse);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getDateOfBirth(),
                employee.getGender(),
                ssnCryptoService.mask(employee.getSsnLastFour()),
                employee.getCreatedAt()
        );
    }
}
