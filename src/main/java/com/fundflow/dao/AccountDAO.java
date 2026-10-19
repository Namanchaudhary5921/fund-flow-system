package com.fundflow.dao;

import com.fundflow.model.Account;
import com.fundflow.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO = Data Access Object: the only place that writes SQL for the account table. */
public class AccountDAO {

    private static Account map(ResultSet rs) throws SQLException {
        return new Account(rs.getString("account_no"), rs.getInt("customer_id"), rs.getString("type"),
                rs.getBigDecimal("balance"), rs.getString("status"));
    }

    public Account findByAccountNo(String accountNo) throws SQLException {
        String sql = "SELECT * FROM account WHERE account_no = ?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public List<Account> findByCustomerId(int customerId) throws SQLException {
        String sql = "SELECT * FROM account WHERE customer_id = ? ORDER BY account_no";
        List<Account> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<String> findActiveAccountNos() throws SQLException {
        List<String> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT account_no FROM account WHERE status='ACTIVE' ORDER BY account_no")) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    /** Sum of every balance. Used by the concurrency test: transfers must never change this total. */
    public BigDecimal totalBalance() throws SQLException {
        try (Connection c = DBConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(balance),0) FROM account")) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }
}
