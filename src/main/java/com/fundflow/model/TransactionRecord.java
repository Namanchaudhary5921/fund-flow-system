package com.fundflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record TransactionRecord(long transId, Timestamp date, String type, BigDecimal amount,
                                String status, String description, String fromAccount, String toAccount) {}
