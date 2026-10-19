package com.fundflow.model;

import java.math.BigDecimal;

public record Account(String accountNo, int customerId, String type, BigDecimal balance, String status) {}
