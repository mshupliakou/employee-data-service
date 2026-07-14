package com.project.employeedataservice.entity;

/**
 * Deliberately kept as a small, explicit set rather than a free-text field.
 * If broader/self-described values are needed in the future, consider
 * relaxing this to a validated free-text field instead of growing this enum
 * indefinitely.
 */
public enum Gender {
    MALE,
    FEMALE,
    NON_BINARY,
    PREFER_NOT_TO_SAY,
    OTHER
}
