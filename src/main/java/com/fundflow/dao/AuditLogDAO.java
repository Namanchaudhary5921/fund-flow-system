package com.fundflow.dao;

import com.fundflow.model.AuditEntry;
import com.fundflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDAO {
    public List<AuditEntry> findRecent(int limit) throws SQLException {
        String sql = "SELECT * FROM audit_log ORDER BY log_time DESC, log_id DESC LIMIT ?";
        List<AuditEntry> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new AuditEntry(rs.getLong("log_id"), rs.getString("account_no"),
                        rs.getString("action"), rs.getBigDecimal("old_balance"), rs.getBigDecimal("new_balance"),
                        rs.getTimestamp("log_time")));
            }
        }
        return list;
    }
}
