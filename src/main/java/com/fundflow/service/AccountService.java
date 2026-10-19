package com.fundflow.service;

import com.fundflow.dao.AccountDAO;
import com.fundflow.model.Account;

import java.sql.SQLException;
import java.util.List;

public class AccountService {
    private final AccountDAO accountDao = new AccountDAO();

    public List<Account> getAccounts(int customerId) throws SQLException { return accountDao.findByCustomerId(customerId); }
    public List<String> allActiveAccountNos() throws SQLException { return accountDao.findActiveAccountNos(); }
}
