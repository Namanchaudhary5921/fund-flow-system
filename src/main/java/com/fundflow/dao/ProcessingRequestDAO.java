package com.fundflow.dao;

import com.fundflow.model.ProcessingRequest;
import com.fundflow.model.RequestType;
import com.fundflow.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;

/** Persists every queued request so the queue history survives restarts and can be audited. */
public class ProcessingRequestDAO {

    /** Inserts a PENDING row and returns its generated id (used as the request id). */
    public long insert(RequestType type, String from, String to, BigDecimal amount, int priority) throws SQLException {
        String sql = "INSERT INTO processing_request (request_type, from_account, to_account, amount, priority) VALUES (?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.name());
            ps.setString(2, from);
            ps.setString(3, to);
            ps.setBigDecimal(4, amount);
            ps.setInt(5, priority);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getLong(1); }
        }
    }

    public void markFinished(ProcessingRequest r) throws SQLException {
        String sql = "UPDATE processing_request SET status=?, result_message=?, completed_at=CURRENT_TIMESTAMP(3) WHERE request_id=?";
        String msg = r.getResult() == null ? "" : r.getResult().message();
        if (msg != null && msg.length() > 200) msg = msg.substring(0, 200);
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getStatus().name());
            ps.setString(2, msg);
            ps.setLong(3, r.getRequestId());
            ps.executeUpdate();
        }
    }
}
