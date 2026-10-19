package com.fundflow.model;

/** The logged-in person (kept in memory for the session; never holds the password hash). */
public record User(int userId, String username, String role, Integer customerId, Integer empId) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
