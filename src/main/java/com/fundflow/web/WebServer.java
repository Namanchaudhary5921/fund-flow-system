package com.fundflow.web;

import com.fundflow.concurrency.TransactionEngine;
import com.fundflow.concurrency.scheduling.Scheduler;
import com.fundflow.concurrency.scheduling.SchedulingAlgorithm;
import com.fundflow.dao.AccountDAO;
import com.fundflow.model.*;
import com.fundflow.service.*;
import com.fundflow.util.DBConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.fundflow.web.Json.*;

/**
 * Web front end: a small HTTP server (JDK built-in, no framework) that serves
 * web/index.html and a JSON API. Every money request goes through the SAME
 * TransactionEngine (queue + scheduler + locks) as the JavaFX version.
 *
 * Run:  mvn compile exec:java -Dexec.mainClass=com.fundflow.web.WebServer
 * Open: http://localhost:8080
 */
public class WebServer {

    private static final Map<String, User> sessions = new ConcurrentHashMap<>();
    private static final AuthService auth = new AuthService();
    private static final AccountDAO accountDao = new AccountDAO();
    private static final TransactionService txService = new TransactionService();
    private static final AdminService adminService = new AdminService();
    private static final SecureRandom random = new SecureRandom();
    private static TransactionEngine engine;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        DBConnection.getConnection().close();          // fail fast: connect to MySQL at startup
        System.out.println("Database connection OK");
        engine = new TransactionEngine(SchedulingAlgorithm.ROUND_ROBIN, 1, 4, 40, 500);
        engine.start();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", WebServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(24));   // handlers wait for results, so use many threads
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { engine.shutdown(); DBConnection.close(); }));
        System.out.println("Fund Flow web app running at http://localhost:" + port);
    }

    // ------------------------------------------------------------------ routing
    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (!path.startsWith("/api/")) { page(ex, path); return; }

            Map<String, Object> body = "POST".equals(ex.getRequestMethod())
                    ? parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)) : Map.of();
            if (path.equals("/api/login")) { login(ex, body); return; }

            User u = session(ex);
            if (u == null) { send(ex, 401, map("error", "Please log in")); return; }
            boolean admin = u.isAdmin();

            switch (path) {
                case "/api/me"       -> send(ex, 200, map("username", u.username(), "role", u.role()));
                case "/api/logout"   -> logout(ex);
                case "/api/accounts" -> {
                    List<Account> list = admin ? accountDao.findAll() : accountDao.findByCustomerId(u.customerId());
                    send(ex, 200, list.stream().map(a -> map("accountNo", a.accountNo(), "type", a.type(),
                            "balance", a.balance(), "status", a.status())).collect(Collectors.toList()));
                }
                case "/api/transactions" -> {
                    List<TransactionRecord> list = admin ? adminService.recentTransactions(200)
                            : txService.history(accountDao.findByCustomerId(u.customerId()).stream().map(Account::accountNo).toList(), 100);
                    send(ex, 200, list.stream().map(t -> map("id", t.transId(), "date", t.date(), "type", t.type(),
                            "amount", t.amount(), "from", t.fromAccount(), "to", t.toAccount(), "status", t.status()))
                            .collect(Collectors.toList()));
                }
                case "/api/deposit", "/api/withdraw", "/api/transfer" -> operate(ex, u, path.substring(5), body);
                default -> {
                    if (!admin) { send(ex, 403, map("error", "Admin only")); return; }
                    adminRoutes(ex, path, body);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            send(ex, 500, map("error", String.valueOf(e)));
        }
    }

    private static void adminRoutes(HttpExchange ex, String path, Map<String, Object> body) throws Exception {
        switch (path) {
            case "/api/audit" -> send(ex, 200, adminService.recentAudit(200).stream().map(a -> map("id", a.logId(),
                    "time", a.logTime(), "account", a.accountNo(), "action", a.action(),
                    "oldBalance", a.oldBalance(), "newBalance", a.newBalance())).collect(Collectors.toList()));
            case "/api/monitor" -> send(ex, 200, monitor());
            case "/api/algorithm" -> {
                SchedulingAlgorithm algo = SchedulingAlgorithm.valueOf(str(body, "algorithm"));
                engine.switchAlgorithm(algo, Math.max(1, Math.min(4, integer(body, "quantum", 1))));
                send(ex, 200, map("message", "Now using " + engine.scheduler().name()));
            }
            case "/api/load" -> {
                int n = Math.max(1, Math.min(300, integer(body, "count", 50)));
                new Thread(() -> fireLoad(n), "load-generator").start();
                send(ex, 200, map("message", "Queued " + n + " random transfers"));
            }
            default -> send(ex, 404, map("error", "Unknown endpoint"));
        }
    }

    // ------------------------------------------------------------------ money operations
    private static void operate(HttpExchange ex, User u, String type, Map<String, Object> body) throws Exception {
        if (u.isAdmin()) { send(ex, 403, map("error", "Log in as a customer to move money")); return; }
        String acc = str(body, "account");
        BigDecimal amount = dec(body, "amount");
        if (acc == null || amount == null) { send(ex, 400, map("error", "Account and amount are required")); return; }
        Account a = accountDao.findByAccountNo(acc);
        if (a == null || a.customerId() != u.customerId()) { send(ex, 403, map("error", "That is not your account")); return; }

        int prio = Math.max(1, Math.min(10, integer(body, "priority", 5)));
        CompletableFuture<OperationResult> f = switch (type) {
            case "deposit"  -> engine.deposit(acc, amount, prio);
            case "withdraw" -> engine.withdraw(acc, amount, prio);
            default         -> engine.transfer(acc, str(body, "to"), amount, prio);
        };
        OperationResult r = f.get(30, TimeUnit.SECONDS);          // wait for a worker thread to finish it
        send(ex, 200, map("success", r.success(), "message", r.message()));
    }

    private static void fireLoad(int n) {
        try {
            List<String> accounts = accountDao.findActiveAccountNos();
            Random rnd = new Random();
            for (int i = 0; i < n && accounts.size() > 1; i++) {
                String from = accounts.get(rnd.nextInt(accounts.size())), to;
                do { to = accounts.get(rnd.nextInt(accounts.size())); } while (to.equals(from));
                engine.transfer(from, to, BigDecimal.valueOf(1 + rnd.nextInt(200)), 1 + rnd.nextInt(10));
            }
        } catch (Exception e) { System.err.println("Load generation failed: " + e.getMessage()); }
    }

    private static Map<String, Object> monitor() {
        Scheduler s = engine.scheduler();
        var m = engine.tracker().metrics();
        return map("algorithm", engine.algorithm().name(), "description", s.description(), "queueSize", s.size(),
                "workers", engine.workerCount(),
                "queue", s.snapshot().stream().limit(15).map(r -> map("id", r.getRequestId(), "type", r.getType().name(),
                        "priority", r.getPriority(), "steps", r.getStepsDone() + "/" + r.getType().burstTime())).collect(Collectors.toList()),
                "completed", m.completed(), "failed", m.failed(),
                "avgWait", Math.round(m.avgWaiting() * 10) / 10.0, "avgTurnaround", Math.round(m.avgTurnaround() * 10) / 10.0,
                "avgResponse", Math.round(m.avgResponse() * 10) / 10.0,
                "lockAcquisitions", engine.locks().getAcquisitions(), "lockWaits", engine.locks().getContentions(),
                "slices", engine.tracker().recentSlices().stream().limit(40).map(x -> map("id", x.requestId(), "type", x.type(),
                        "worker", x.worker(), "from", x.fromStep(), "to", x.toStep(), "ms", x.endMs() - x.startMs(), "end", x.endMs()))
                        .collect(Collectors.toList()));
    }

    // ------------------------------------------------------------------ sessions
    private static void login(HttpExchange ex, Map<String, Object> body) throws Exception {
        String user = str(body, "username"), pass = str(body, "password");
        if (user == null || pass == null) { send(ex, 400, map("error", "Enter username and password")); return; }
        Optional<User> u = auth.login(user, pass);
        if (u.isEmpty()) { send(ex, 401, map("error", "Wrong username or password")); return; }
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        sessions.put(token, u.get());
        ex.getResponseHeaders().add("Set-Cookie", "FFSESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
        send(ex, 200, map("username", u.get().username(), "role", u.get().role()));
    }

    private static void logout(HttpExchange ex) throws IOException {
        String t = token(ex);
        if (t != null) sessions.remove(t);
        ex.getResponseHeaders().add("Set-Cookie", "FFSESSION=; Path=/; Max-Age=0");
        send(ex, 200, map("ok", true));
    }

    private static String token(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("FFSESSION=")) return p.substring(10);
        }
        return null;
    }

    private static User session(HttpExchange ex) { String t = token(ex); return t == null ? null : sessions.get(t); }

    // ------------------------------------------------------------------ responses
    private static void page(HttpExchange ex, String path) throws IOException {
        if (!path.equals("/") && !path.equals("/index.html")) { raw(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
        try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
            raw(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
        }
    }

    private static void send(HttpExchange ex, int status, Object json) throws IOException {
        raw(ex, status, "application/json; charset=utf-8", write(json).getBytes(StandardCharsets.UTF_8));
    }

    private static void raw(HttpExchange ex, int status, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (var out = ex.getResponseBody()) { out.write(data); }
    }
}
