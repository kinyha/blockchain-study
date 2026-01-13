package com.study.blockchain.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Displays mining progress: nonce counter, current hash, progress bar.
 */
public class MiningPane extends HBox {

    private Label statusLabel;
    private Label nonceLabel;
    private Label hashLabel;
    private ProgressBar progressBar;

    public MiningPane() {
        setSpacing(15);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #34495e; -fx-background-radius: 5;");

        statusLabel = new Label("Mining: Idle");
        statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");

        nonceLabel = new Label("Nonce: 0");
        nonceLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-family: monospace;");

        hashLabel = new Label("Hash: -");
        hashLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-family: monospace; -fx-font-size: 10;");
        hashLabel.setMaxWidth(300);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(statusLabel, nonceLabel, hashLabel, spacer, progressBar);
    }

    public void setMining(boolean mining) {
        if (mining) {
            statusLabel.setText("Mining: Active");
            statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        } else {
            statusLabel.setText("Mining: Idle");
            statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");
            progressBar.setVisible(false);
            progressBar.setProgress(0);
        }
    }

    public void update(long nonce, String hash, String target) {
        nonceLabel.setText("Nonce: " + nonce);

        String displayHash = hash.length() > 24 ? hash.substring(0, 24) + "..." : hash;
        hashLabel.setText("Hash: " + displayHash);

        // Color the hash based on how close it is to target
        int leadingZeros = countLeadingZeros(hash);
        int targetZeros = target.length();

        if (leadingZeros >= targetZeros) {
            hashLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-family: monospace; -fx-font-size: 10;");
        } else if (leadingZeros > 0) {
            hashLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-family: monospace; -fx-font-size: 10;");
        } else {
            hashLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-family: monospace; -fx-font-size: 10;");
        }
    }

    private int countLeadingZeros(String hash) {
        int count = 0;
        for (char c : hash.toCharArray()) {
            if (c == '0') count++;
            else break;
        }
        return count;
    }
}
