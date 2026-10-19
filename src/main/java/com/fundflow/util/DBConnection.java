package com.fundflow.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** JDBC connection POOL (HikariCP): connections are reused instead of re-opened for every query. */
public final class DBConnection {
    private static final HikariDataSource ds;

    static {
        Properties p = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            p.load(in);
        } catch (IOException | NullPointerException e) {
            throw new ExceptionInInitializerError("Cannot read db.properties: " + e);
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getProperty("db.url"));
        cfg.setUsername(p.getProperty("db.user"));
        cfg.setPassword(p.getProperty("db.password"));
        cfg.setMaximumPoolSize(Integer.parseInt(p.getProperty("db.poolSize", "12")));
        ds = new HikariDataSource(cfg);
    }

    private DBConnection() {}

    public static Connection getConnection() throws SQLException { return ds.getConnection(); }
    public static void close() { ds.close(); }
}
