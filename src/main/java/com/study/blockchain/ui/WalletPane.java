package com.study.blockchain.ui;

import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Displays wallet information: balance and address.
 */
public class WalletPane extends VBox {

    private final Wallet wallet;
    private final UTXOPool utxoPool;

    private Label balanceLabel;
    private TextField addressField;

    public WalletPane(Wallet wallet, UTXOPool utxoPool) {
        this.wallet = wallet;
        this.utxoPool = utxoPool;

        setSpacing(10);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #fff; -fx-background-radius: 5; " +
                "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("Wallet");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // Balance
        HBox balanceBox = new HBox(10);
        Label balanceTitle = new Label("Balance:");
        balanceLabel = new Label("0.0 BTC");
        balanceLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        balanceBox.getChildren().addAll(balanceTitle, balanceLabel);

        // Address
        VBox addressBox = new VBox(5);
        Label addressTitle = new Label("Your Address:");
        addressField = new TextField(wallet.getAddress().substring(0, 32) + "...");
        addressField.setEditable(false);
        addressField.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");

        Button copyButton = new Button("Copy");
        copyButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(wallet.getAddress());
            clipboard.setContent(content);
        });

        HBox addressRow = new HBox(5, addressField, copyButton);
        addressBox.getChildren().addAll(addressTitle, addressRow);

        getChildren().addAll(titleLabel, new Separator(), balanceBox, addressBox);
    }

    public void refresh() {
        double balance = utxoPool.getBalance(wallet.getPublicKey());
        balanceLabel.setText(String.format("%.2f BTC", balance));
    }
}
