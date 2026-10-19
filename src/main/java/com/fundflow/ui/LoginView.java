package com.fundflow.ui;

import com.fundflow.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class LoginView {

    public Parent build() {
        Label title = new Label("Fund Flow Management System");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b;");
        Button login = new Button("Login");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);

        login.setOnAction(e -> {
            try {
                Optional<User> user = AppContext.authService.login(username.getText(), password.getText());
                if (user.isPresent()) MainApp.showDashboard(user.get());
                else error.setText("Invalid username or password");
            } catch (Exception ex) {
                error.setText("Database error: " + ex.getMessage());
            }
        });

        Label hint = new Label("Demo: admin/admin123   alice/password123");
        hint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox box = new VBox(12, title, username, password, login, error, hint);
        box.setPadding(new Insets(30));
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
