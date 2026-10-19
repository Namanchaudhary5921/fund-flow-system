package com.fundflow.service;

import com.fundflow.dao.UserDAO;
import com.fundflow.model.User;
import com.fundflow.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Optional;

public class AuthService {
    private final UserDAO userDao = new UserDAO();

    /** Returns the user if username + password match, otherwise empty. */
    public Optional<User> login(String username, String password) throws SQLException {
        Optional<UserDAO.UserRow> row = userDao.findByUsername(username.trim());
        if (row.isEmpty() || !PasswordUtil.verify(password, row.get().passwordHash())) return Optional.empty();
        UserDAO.UserRow u = row.get();
        return Optional.of(new User(u.userId(), u.username(), u.role(), u.customerId(), u.empId()));
    }
}
