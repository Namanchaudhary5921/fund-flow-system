package com.fundflow.service;

import com.fundflow.dao.TransactionDAO;
import com.fundflow.model.OperationResult;
import com.fundflow.model.TransactionRecord;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Direct (single-threaded, no queue) operations. This is the BASELINE version:
 * useful to test the stored procedures and to compare with the queued engine.
 * The JavaFX screens use TransactionEngine (queue + scheduler) instead.
 */
public class TransactionService {
    private final TransactionDAO dao = new TransactionDAO();

    public OperationResult deposit(String acc, BigDecimal amt) throws SQLException { return dao.deposit(acc, amt); }
    public OperationResult withdraw(String acc, BigDecimal amt) throws SQLException { return dao.withdraw(acc, amt); }
    public OperationResult transfer(String from, String to, BigDecimal amt) throws SQLException { return dao.transfer(from, to, amt); }

    public List<TransactionRecord> history(List<String> accountNos, int limit) throws SQLException { return dao.findByAccounts(accountNos, limit); }
}
