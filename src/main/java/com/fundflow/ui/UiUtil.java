package com.fundflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

public final class UiUtil {
    private UiUtil() {}

    /** Builds a text column from a lambda (works with Java records, unlike PropertyValueFactory). */
    public static <T> TableColumn<T, String> col(String title, double width, Function<T, String> getter) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    public static void error(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
