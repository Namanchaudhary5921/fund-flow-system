package com.fundflow.dao;

import com.fundflow.model.OperationResult;
import com.fundflow.model.TransactionRecord;
import com.fundflow.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Calls the stored procedures (all ACID logic lives inside MySQL) and reads history. */
public class TransactionDAO {

    public OperationResult deposit(String account, BigDecimal amount) throws SQLException {
        return call("{CALL sp_deposit(?,?,?,?)}", account, amount);
    }

    public OperationResult withdraw(String account, BigDecimal amount) throws SQLException {
        return call("{CALL sp_withdraw(?,?,?,?)}", account, amount);
    }

    public OperationResult transfer(String from, String to, BigDecimal amount) throws SQLException {
        return call("{CALL sp_transfer_funds(?,?,?,?,?)}", from, to, amount);
    }

    /** IN parameters first, then the two OUT parameters (status, message) are always last. */
    private OperationResult call(String sql, Object... in) throws SQLException {
        try (Connection c = DBConnection.getConnection(); CallableStatement cs = c.prepareCall(sql)) {
            int i = 1;
            for (Object o : in) cs.setObject(i++, o);
            cs.registerOutParameter(i, Types.VARCHAR);
            cs.registerOutParameter(i + 1, Types.VARCHAR);
            cs.execute();
            return new OperationResult("SUCCESS".equals(cs.getString(i)), cs.getString(i + 1));
        }
    }

    private static TransactionRecord map(ResultSet rs) throws SQLException {
        return new TransactionRecord(rs.getLong("trans_id"), rs.getTimestamp("trans_date"), rs.getString("type"),
                rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("description"),
                rs.getString("from_account"), rs.getString("to_account"));
    }

    public List<TransactionRecord> findRecent(int limit) throws SQLException {
        String sql = "SELECT * FROM transactions ORDER BY trans_date DESC, trans_id DESC LIMIT ?";
        List<TransactionRecord> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    /** History of one customer = every transaction touching any of his accounts. */
    public List<TransactionRecord> findByAccounts(List<String> accountNos, int limit) throws SQLException {
        List<TransactionRecord> list = new ArrayList<>();
        if (accountNos.isEmpty()) return list;
        String marks = String.join(",", accountNos.stream().map(a -> "?").toList());
        String sql = "SELECT * FROM transactions WHERE from_account IN (" + marks + ") OR to_account IN (" + marks
                + ") ORDER BY trans_date DESC, trans_id DESC LIMIT ?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (String a : accountNos) ps.setString(i++, a);
            for (String a : accountNos) ps.setString(i++, a);
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }
}
