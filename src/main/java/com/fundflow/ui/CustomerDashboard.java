package com.fundflow.ui;

import com.fundflow.model.Account;
import com.fundflow.model.OperationResult;
import com.fundflow.model.TransactionRecord;
import com.fundflow.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Customer screen: balances, deposit / withdraw / transfer (through the scheduler queue) and history. */
public class CustomerDashboard {

    private final User user;
    private final TableView<Account> accountTable = new TableView<>();
    private final TableView<TransactionRecord> historyTable = new TableView<>();
    private final ComboBox<String> accountBox = new ComboBox<>();
    private final ChoiceBox<String> typeBox = new ChoiceBox<>(FXCollections.observableArrayList("Deposit", "Withdraw", "Transfer"));
    private final TextField toField = new TextField();
    private final TextField amountField = new TextField();
    private final Label status = new Label();

    public CustomerDashboard(User user) { this.user = user; }

    public Parent build() {
        // ---- header
        Label welcome = new Label("Welcome, " + user.username());
        welcome.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button logout = new Button("Logout");
        logout.setOnAction(e -> MainApp.showLogin());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, welcome, spacer, logout);
        header.setPadding(new Insets(10));

        // ---- accounts table
        accountTable.getColumns().addAll(
                UiUtil.<Account>col("Account No", 120, Account::accountNo),
                UiUtil.<Account>col("Type", 100, Account::type),
                UiUtil.<Account>col("Balance", 140, a -> a.balance().toPlainString()),
                UiUtil.<Account>col("Status", 90, Account::status));
        accountTable.setPrefHeight(160);

        // ---- operation form
        typeBox.setValue("Deposit");
        accountBox.setPromptText("Your account");
        toField.setPromptText("Destination account (transfer)");
        amountField.setPromptText("Amount");
        Button submit = new Button("Submit to queue");
        submit.setOnAction(e -> submit());
        typeBox.valueProperty().addListener((o, a, b) -> toField.setDisable(!"Transfer".equals(b)));
        toField.setDisable(true);

        HBox form = new HBox(10, typeBox, accountBox, toField, amountField, submit);
        form.setPadding(new Insets(10, 0, 0, 0));
        VBox opBox = new VBox(6, new Label("New operation"), form, status);

        // ---- history table
        historyTable.getColumns().addAll(
                UiUtil.<TransactionRecord>col("ID", 60, t -> String.valueOf(t.transId())),
                UiUtil.<TransactionRecord>col("Date", 170, t -> String.valueOf(t.date())),
                UiUtil.<TransactionRecord>col("Type", 90, TransactionRecord::type),
                UiUtil.<TransactionRecord>col("Amount", 110, t -> t.amount().toPlainString()),
                UiUtil.<TransactionRecord>col("From", 90, t -> String.valueOf(t.fromAccount() == null ? "-" : t.fromAccount())),
                UiUtil.<TransactionRecord>col("To", 90, t -> String.valueOf(t.toAccount() == null ? "-" : t.toAccount())),
                UiUtil.<TransactionRecord>col("Description", 260, TransactionRecord::description));
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        VBox center = new VBox(10, new Label("My accounts"), accountTable, opBox, new Label("Transaction history"), historyTable);
        center.setPadding(new Insets(10));

        BorderPane root = new BorderPane(center);
        root.setTop(header);
        refresh();
        return root;
    }

    private void refresh() {
        try {
            List<Account> accounts = AppContext.accountService.getAccounts(user.customerId());
            accountTable.setItems(FXCollections.observableArrayList(accounts));
            List<String> nos = accounts.stream().map(Account::accountNo).toList();
            String selected = accountBox.getValue();
            accountBox.setItems(FXCollections.observableArrayList(nos));
            accountBox.setValue(selected != null ? selected : (nos.isEmpty() ? null : nos.get(0)));
            historyTable.setItems(FXCollections.observableArrayList(AppContext.transactionService.history(nos, 100)));
        } catch (Exception e) {
            status.setText("Could not load data: " + e.getMessage());
        }
    }

    private void submit() {
        BigDecimal amount;
        try { amount = new BigDecimal(amountField.getText().trim()); }
        catch (Exception e) { status.setText("Enter a valid amount"); return; }
        String acc = accountBox.getValue();
        if (acc == null) { status.setText("Select your account"); return; }

        CompletableFuture<OperationResult> future = switch (typeBox.getValue()) {
            case "Deposit"  -> AppContext.engine.deposit(acc, amount, 5);
            case "Withdraw" -> AppContext.engine.withdraw(acc, amount, 5);
            default         -> AppContext.engine.transfer(acc, toField.getText().trim(), amount, 5);
        };
        status.setText("Request queued - waiting for a worker thread...");

        // Runs later on a worker thread; hop back to the JavaFX thread to touch the UI.
        future.thenAccept(res -> Platform.runLater(() -> {
            status.setText((res.success() ? "SUCCESS: " : "FAILED: ") + res.message());
            refresh();
        }));
    }
}
