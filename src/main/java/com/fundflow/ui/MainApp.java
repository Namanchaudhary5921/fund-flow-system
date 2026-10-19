package com.fundflow.ui;

import com.fundflow.concurrency.TransactionEngine;
import com.fundflow.concurrency.scheduling.SchedulingAlgorithm;
import com.fundflow.model.User;
import com.fundflow.util.DBConnection;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Entry point. Run with:  mvn javafx:run */
public class MainApp extends Application {

    private static Stage stage;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        // Round Robin, quantum 1, 4 worker threads, 40 ms simulated work per step (so the monitor is readable)
        AppContext.engine = new TransactionEngine(SchedulingAlgorithm.ROUND_ROBIN, 4, 40);
        AppContext.engine.start();
        stage.setTitle("Fund Flow Management System");
        showLogin();
        stage.show();
    }

    public static void showLogin() {
        AppContext.currentUser = null;
        stage.setScene(new Scene(new LoginView().build(), 420, 320));
    }

    public static void showDashboard(User user) {
        AppContext.currentUser = user;
        stage.setScene(user.isAdmin()
                ? new Scene(new AdminDashboard().build(), 1100, 700)
                : new Scene(new CustomerDashboard(user).build(), 1000, 650));
    }

    @Override
    public void stop() {
        AppContext.engine.shutdown();
        DBConnection.close();
    }

    public static void main(String[] args) { launch(args); }
}
