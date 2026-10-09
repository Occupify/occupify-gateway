package com.occupify.gateway.enums;

public enum UserRole {
    USER,
    ADMIN;

    public static boolean isAdmin(String role) {
        return ADMIN.name().equalsIgnoreCase(role);
    }
}
