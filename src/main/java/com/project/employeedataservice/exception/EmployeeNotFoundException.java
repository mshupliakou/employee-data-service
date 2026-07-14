package com.project.employeedataservice.exception;

import java.util.UUID;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(UUID id) {
        super("No employee found with id " + id);
    }
}
