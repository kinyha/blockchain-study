package com.study.blockchain.ui;

import com.study.blockchain.network.Node;
import com.study.blockchain.network.PeerConnection;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Displays connected peers.
 */
public class PeersPane extends HBox {

    private final Node node;
    private Label peersLabel;

    public PeersPane(Node node) {
        this.node = node;

        setSpacing(10);
        setPadding(new Insets(5, 10, 5, 10));
        setStyle("-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Peers:");
        titleLabel.setStyle("-fx-text-fill: #ecf0f1;");

        peersLabel = new Label("None connected");
        peersLabel.setStyle("-fx-text-fill: #bdc3c7;");

        getChildren().addAll(titleLabel, peersLabel);
    }

    public void refresh() {
        int peerCount = node.getPeers().size();

        if (peerCount == 0) {
            peersLabel.setText("None connected");
            peersLabel.setStyle("-fx-text-fill: #bdc3c7;");
        } else {
            StringBuilder sb = new StringBuilder();
            for (PeerConnection peer : node.getPeers()) {
                if (sb.length() > 0) sb.append(", ");
                String peerId = peer.getPeerId();
                String addr = peer.getRemoteAddress();
                sb.append(peerId != null ? peerId : addr);
            }
            peersLabel.setText(peerCount + " connected: " + sb);
            peersLabel.setStyle("-fx-text-fill: #2ecc71;");
        }
    }
}
