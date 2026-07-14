package com.project.employeedataservice.repository;

import com.project.employeedataservice.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsBySsnHash(String ssnHash);
}
