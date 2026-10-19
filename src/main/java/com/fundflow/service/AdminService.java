package com.fundflow.service;

import com.fundflow.dao.AuditLogDAO;
import com.fundflow.dao.TransactionDAO;
import com.fundflow.model.AuditEntry;
import com.fundflow.model.TransactionRecord;

import java.sql.SQLException;
import java.util.List;

public class AdminService {
    private final TransactionDAO transactionDao = new TransactionDAO();
    private final AuditLogDAO auditDao = new AuditLogDAO();

    public List<TransactionRecord> recentTransactions(int limit) throws SQLException { return transactionDao.findRecent(limit); }
    public List<AuditEntry> recentAudit(int limit) throws SQLException { return auditDao.findRecent(limit); }
}
