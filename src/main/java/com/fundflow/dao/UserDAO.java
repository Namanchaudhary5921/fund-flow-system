package com.fundflow.dao;

import com.fundflow.util.DBConnection;

import java.sql.*;
import java.util.Optional;

public class UserDAO {

    public record UserRow(int userId, String username, String passwordHash, String role,
                          Integer customerId, Integer empId) {}

    public Optional<UserRow> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM user_login WHERE username = ?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                int cid = rs.getInt("customer_id"); Integer customerId = rs.wasNull() ? null : cid;
                int eid = rs.getInt("emp_id");      Integer empId = rs.wasNull() ? null : eid;
                return Optional.of(new UserRow(rs.getInt("user_id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("role"), customerId, empId));
            }
        }
    }

    public void insert(String username, String hash, String role, Integer customerId, Integer empId) throws SQLException {
        String sql = "INSERT INTO user_login (username, password_hash, role, customer_id, emp_id) VALUES (?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username); ps.setString(2, hash); ps.setString(3, role);
            ps.setObject(4, customerId); ps.setObject(5, empId);
            ps.executeUpdate();
        }
    }
}
