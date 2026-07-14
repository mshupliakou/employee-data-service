package com.project.employeedataservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The SSN is never stored in plaintext:
 *  - ssnEncrypted: AES-256-GCM ciphertext (reversible, for legitimate authorized use e.g. tax filing)
 *  - ssnHash: deterministic HMAC-SHA256 (one-way, used only to enforce "one record per SSN" without
 *             ever decrypting anything)
 *  - ssnLastFour: last 4 digits, kept in the clear because it's the conventional "safe to display"
 *                 fragment of an SSN and lets the API return a masked value cheaply.
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_employees_ssn_hash", columnNames = "ssn_hash")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Gender gender;

    @Column(name = "ssn_encrypted", nullable = false, columnDefinition = "TEXT")
    private String ssnEncrypted;

    @Column(name = "ssn_hash", nullable = false, unique = true, length = 64)
    private String ssnHash;

    @Column(name = "ssn_last_four", nullable = false, length = 4)
    private String ssnLastFour;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
