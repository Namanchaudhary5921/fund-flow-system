package com.fundflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record AuditEntry(long logId, String accountNo, String action,
                         BigDecimal oldBalance, BigDecimal newBalance, Timestamp logTime) {}
