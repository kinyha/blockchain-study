package com.study.blockchain.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Form for sending transactions.
 */
public class SendForm extends VBox {

    private final BiConsumer<String, Double> onSend;

    private ComboBox<String> recipientCombo;
    private TextField amountField;
    private Label statusLabel;

    public SendForm(BiConsumer<String, Double> onSend) {
        this.onSend = onSend;

        setSpacing(10);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #fff; -fx-background-radius: 5; " +
                "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("Send Transaction");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // Recipient selection
        VBox toBox = new VBox(5);
        Label toLabel = new Label("To (select peer wallet):");
        recipientCombo = new ComboBox<>();
        recipientCombo.setPromptText("No peers connected...");
        recipientCombo.setMaxWidth(Double.MAX_VALUE);
        toBox.getChildren().addAll(toLabel, recipientCombo);

        // Amount
        VBox amountBox = new VBox(5);
        Label amountLabel = new Label("Amount (BTC):");
        amountField = new TextField();
        amountField.setPromptText("0.0");
        amountBox.getChildren().addAll(amountLabel, amountField);

        // Status label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11;");

        // Send button
        Button sendButton = new Button("Send Transaction");
        sendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-cursor: hand;");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> handleSend());

        getChildren().addAll(titleLabel, new Separator(), toBox, amountBox, statusLabel, sendButton);
    }

    public void updatePeerWallets(Set<String> addresses) {
        String selected = recipientCombo.getValue();
        recipientCombo.getItems().clear();
        recipientCombo.getItems().addAll(addresses);

        if (addresses.isEmpty()) {
            recipientCombo.setPromptText("No peers connected...");
            statusLabel.setText("Connect to a peer to send");
        } else {
            recipientCombo.setPromptText("Select recipient...");
            statusLabel.setText(addresses.size() + " peer wallet(s) available");

            // Restore selection if still valid
            if (selected != null && addresses.contains(selected)) {
                recipientCombo.setValue(selected);
            }
        }
    }

    private void handleSend() {
        String toAddress = recipientCombo.getValue();
        String amountText = amountField.getText().trim();

        if (toAddress == null || toAddress.isEmpty()) {
            showError("Please select a recipient");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                showError("Amount must be positive");
                return;
            }

            onSend.accept(toAddress, amount);

            // Clear form
            amountField.clear();

        } catch (NumberFormatException e) {
            showError("Invalid amount");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
