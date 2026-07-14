package com.project.employeedataservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.employeedataservice.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ssnMasked only ever exposes the last four digits (e.g. "***-**-1234").
 * The encrypted value and hash never leave the service.
 */
public record EmployeeResponse(
        UUID id,
        String firstName,
        String lastName,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateOfBirth,
        Gender gender,
        String ssnMasked,
        Instant createdAt
) {
}
