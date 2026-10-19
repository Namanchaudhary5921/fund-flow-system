package com.fundflow;

import com.fundflow.dao.UserDAO;
import com.fundflow.util.DBConnection;
import com.fundflow.util.PasswordUtil;

/**
 * Creates the login users (BCrypt needs Java, so it cannot be done in plain SQL).
 * Run ONCE after the 3 SQL scripts:
 *     mvn compile exec:java -Dexec.mainClass=com.fundflow.SeedData
 *
 *   admin / admin123        (ADMIN, employee 1)
 *   alice / password123     (customer 1)    bob / password123 (customer 2)    carol / password123 (customer 3)
 */
public class SeedData {
    public static void main(String[] args) throws Exception {
        UserDAO dao = new UserDAO();
        create(dao, "admin", "admin123",    "ADMIN",    null, 1);
        create(dao, "alice", "password123", "CUSTOMER", 1, null);
        create(dao, "bob",   "password123", "CUSTOMER", 2, null);
        create(dao, "carol", "password123", "CUSTOMER", 3, null);
        DBConnection.close();
    }

    private static void create(UserDAO dao, String user, String pass, String role, Integer cust, Integer emp) throws Exception {
        if (dao.findByUsername(user).isPresent()) { System.out.println("exists : " + user); return; }
        dao.insert(user, PasswordUtil.hash(pass), role, cust, emp);
        System.out.println("created: " + user);
    }
}
