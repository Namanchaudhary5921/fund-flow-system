package com.fundflow.ui;

import com.fundflow.concurrency.ExecutionTracker;
import com.fundflow.concurrency.scheduling.SchedulingAlgorithm;
import com.fundflow.model.AuditEntry;
import com.fundflow.model.ProcessingRequest;
import com.fundflow.model.TransactionRecord;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Admin screen: all transactions, audit log, and the live OS monitor (the demo screen). */
public class AdminDashboard {

    private final TableView<TransactionRecord> txTable = new TableView<>();
    private final TableView<AuditEntry> auditTable = new TableView<>();
    private final TableView<ExecutionTracker.Slice> ganttTable = new TableView<>();
    private final Label queueInfo = new Label();
    private final Label metricsInfo = new Label();
    private final Label queueContent = new Label();
    private Timeline timeline;

    public Parent build() {
        Label title = new Label("Admin Console");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button logout = new Button("Logout");
        logout.setOnAction(e -> { if (timeline != null) timeline.stop(); MainApp.showLogin(); });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, logout);
        header.setPadding(new Insets(10));

        TabPane tabs = new TabPane(transactionsTab(), auditTab(), monitorTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane(tabs);
        root.setTop(header);
        return root;
    }

    // ------------------------------------------------------------ tab 1
    private Tab transactionsTab() {
        txTable.getColumns().addAll(
                UiUtil.<TransactionRecord>col("ID", 60, t -> String.valueOf(t.transId())),
                UiUtil.<TransactionRecord>col("Date", 180, t -> String.valueOf(t.date())),
                UiUtil.<TransactionRecord>col("Type", 90, TransactionRecord::type),
                UiUtil.<TransactionRecord>col("Amount", 110, t -> t.amount().toPlainString()),
                UiUtil.<TransactionRecord>col("From", 90, t -> t.fromAccount() == null ? "-" : t.fromAccount()),
                UiUtil.<TransactionRecord>col("To", 90, t -> t.toAccount() == null ? "-" : t.toAccount()),
                UiUtil.<TransactionRecord>col("Status", 90, TransactionRecord::status));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> loadTransactions());
        VBox.setVgrow(txTable, Priority.ALWAYS);
        loadTransactions();
        return new Tab("Transactions", new VBox(8, refresh, txTable));
    }

    private void loadTransactions() {
        try { txTable.setItems(FXCollections.observableArrayList(AppContext.adminService.recentTransactions(300))); }
        catch (Exception e) { UiUtil.error(e.getMessage()); }
    }

    // ------------------------------------------------------------ tab 2
    private Tab auditTab() {
        auditTable.getColumns().addAll(
                UiUtil.<AuditEntry>col("Log ID", 70, a -> String.valueOf(a.logId())),
                UiUtil.<AuditEntry>col("Time", 190, a -> String.valueOf(a.logTime())),
                UiUtil.<AuditEntry>col("Account", 100, AuditEntry::accountNo),
                UiUtil.<AuditEntry>col("Action", 90, AuditEntry::action),
                UiUtil.<AuditEntry>col("Old balance", 130, a -> a.oldBalance().toPlainString()),
                UiUtil.<AuditEntry>col("New balance", 130, a -> a.newBalance().toPlainString()));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> loadAudit());
        VBox.setVgrow(auditTable, Priority.ALWAYS);
        loadAudit();
        return new Tab("Audit Log (trigger)", new VBox(8, refresh, auditTable));
    }

    private void loadAudit() {
        try { auditTable.setItems(FXCollections.observableArrayList(AppContext.adminService.recentAudit(300))); }
        catch (Exception e) { UiUtil.error(e.getMessage()); }
    }

    // ------------------------------------------------------------ tab 3 : OS monitor
    private Tab monitorTab() {
        ChoiceBox<SchedulingAlgorithm> algoBox = new ChoiceBox<>(FXCollections.observableArrayList(SchedulingAlgorithm.values()));
        algoBox.setValue(AppContext.engine.algorithm());
        Spinner<Integer> quantum = new Spinner<>(1, 4, 1);
        quantum.setPrefWidth(70);
        Button apply = new Button("Apply algorithm");
        apply.setOnAction(e -> new Thread(() ->
                AppContext.engine.switchAlgorithm(algoBox.getValue(), quantum.getValue()), "switch-algo").start());

        Spinner<Integer> count = new Spinner<>(10, 500, 50, 10);
        count.setPrefWidth(90);
        Button fire = new Button("Fire random transfers");
        fire.setOnAction(e -> fireLoad(count.getValue()));

        HBox controls = new HBox(10, new Label("Algorithm:"), algoBox, new Label("RR quantum:"), quantum, apply,
                new Separator(javafx.geometry.Orientation.VERTICAL), new Label("Load:"), count, fire);
        controls.setPadding(new Insets(0, 0, 8, 0));

        ganttTable.getColumns().addAll(
                UiUtil.<ExecutionTracker.Slice>col("Request", 80, s -> "#" + s.requestId()),
                UiUtil.<ExecutionTracker.Slice>col("Type", 90, ExecutionTracker.Slice::type),
                UiUtil.<ExecutionTracker.Slice>col("Worker thread", 120, ExecutionTracker.Slice::worker),
                UiUtil.<ExecutionTracker.Slice>col("Steps run", 100, s -> s.fromStep() + " -> " + s.toStep()),
                UiUtil.<ExecutionTracker.Slice>col("Duration (ms)", 110, s -> String.valueOf(s.endMs() - s.startMs())),
                UiUtil.<ExecutionTracker.Slice>col("Ended at", 120, s -> String.valueOf(s.endMs() % 100000)));
        VBox.setVgrow(ganttTable, Priority.ALWAYS);

        queueInfo.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(8, controls, queueInfo, queueContent, metricsInfo,
                new Label("Execution log (latest first) - every row is one time slice of one request:"), ganttTable);
        box.setPadding(new Insets(10));

        timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> refreshMonitor()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        return new Tab("OS Monitor", box);
    }

    private void refreshMonitor() {
        var engine = AppContext.engine;
        var scheduler = engine.scheduler();
        queueInfo.setText(scheduler.description() + "   |   waiting in queue: " + scheduler.size()
                + "   |   worker threads: " + engine.workerCount());

        List<ProcessingRequest> waiting = scheduler.snapshot();
        String first = waiting.stream().limit(12).map(ProcessingRequest::toString).collect(Collectors.joining("  ->  "));
        queueContent.setText("Queue head: " + (first.isEmpty() ? "(empty)" : first));

        var m = engine.tracker().metrics();
        metricsInfo.setText(String.format("Done: %d ok, %d failed   |   avg waiting %.1f ms   avg turnaround %.1f ms   avg response %.1f ms   |   lock acquisitions %d, lock waits %d",
                m.completed(), m.failed(), m.avgWaiting(), m.avgTurnaround(), m.avgResponse(),
                engine.locks().getAcquisitions(), engine.locks().getContentions()));

        ganttTable.setItems(FXCollections.observableArrayList(engine.tracker().recentSlices()));
    }

    /** Submits n random transfers from a background thread (submitting touches the DB, so not on the UI thread). */
    private void fireLoad(int n) {
        new Thread(() -> {
            try {
                List<String> accounts = AppContext.accountService.allActiveAccountNos();
                if (accounts.size() < 2) return;
                Random rnd = new Random();
                for (int i = 0; i < n; i++) {
                    String from = accounts.get(rnd.nextInt(accounts.size()));
                    String to;
                    do { to = accounts.get(rnd.nextInt(accounts.size())); } while (to.equals(from));
                    AppContext.engine.transfer(from, to, BigDecimal.valueOf(1 + rnd.nextInt(200)), 1 + rnd.nextInt(10));
                }
            } catch (Exception e) { System.err.println("Load generation failed: " + e.getMessage()); }
        }, "load-generator").start();
    }
}
