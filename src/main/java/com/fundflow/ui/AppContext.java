package com.fundflow.ui;

import com.fundflow.concurrency.TransactionEngine;
import com.fundflow.model.User;
import com.fundflow.service.*;

/** Shared objects for all screens (poor man's dependency injection). */
public final class AppContext {
    public static final AuthService authService = new AuthService();
    public static final AccountService accountService = new AccountService();
    public static final TransactionService transactionService = new TransactionService();
    public static final AdminService adminService = new AdminService();

    public static TransactionEngine engine;   // created in MainApp.start()
    public static User currentUser;

    private AppContext() {}
}
