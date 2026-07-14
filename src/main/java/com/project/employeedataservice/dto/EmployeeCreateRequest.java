package com.project.employeedataservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.employeedataservice.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeeCreateRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,

        @NotNull(message = "gender is required")
        Gender gender,

        @NotBlank(message = "socialSecurityNumber is required")
        @Pattern(
                regexp = "^\\d{3}-\\d{2}-\\d{4}$",
                message = "socialSecurityNumber must match the format XXX-XX-XXXX"
        )
        String socialSecurityNumber
) {
}
