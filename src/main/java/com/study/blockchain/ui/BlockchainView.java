package com.study.blockchain.ui;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Visual representation of the blockchain as connected blocks.
 */
public class BlockchainView extends ScrollPane {

    private static final int BLOCK_WIDTH = 120;
    private static final int BLOCK_HEIGHT = 80;
    private static final int BLOCK_SPACING = 40;
    private static final int PADDING = 20;

    private final Blockchain blockchain;
    private final Pane canvasPane;
    private Canvas canvas;
    private Tooltip tooltip;

    public BlockchainView(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.canvasPane = new Pane();
        this.tooltip = new Tooltip();

        setContent(canvasPane);
        setFitToHeight(true);
        setPannable(true);
        setStyle("-fx-background-color: #ecf0f1;");

        refresh();
    }

    public void refresh() {
        int chainSize = blockchain.size();
        int canvasWidth = Math.max(600, PADDING * 2 + chainSize * (BLOCK_WIDTH + BLOCK_SPACING));

        canvas = new Canvas(canvasWidth, 200);
        canvasPane.getChildren().clear();
        canvasPane.getChildren().add(canvas);

        draw();

        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseClicked(this::handleMouseClick);
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear background
        gc.setFill(Color.web("#ecf0f1"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int y = 60;

        for (int i = 0; i < blockchain.size(); i++) {
            Block block = blockchain.getBlock(i);
            int x = PADDING + i * (BLOCK_WIDTH + BLOCK_SPACING);

            // Draw arrow (except for first block)
            if (i > 0) {
                gc.setStroke(Color.web("#7f8c8d"));
                gc.setLineWidth(2);
                int arrowX = x - BLOCK_SPACING + 5;
                gc.strokeLine(arrowX, y + BLOCK_HEIGHT / 2, x - 5, y + BLOCK_HEIGHT / 2);
                // Arrow head
                gc.strokeLine(x - 15, y + BLOCK_HEIGHT / 2 - 8, x - 5, y + BLOCK_HEIGHT / 2);
                gc.strokeLine(x - 15, y + BLOCK_HEIGHT / 2 + 8, x - 5, y + BLOCK_HEIGHT / 2);
            }

            // Draw block
            drawBlock(gc, block, x, y);
        }
    }

    private void drawBlock(GraphicsContext gc, Block block, int x, int y) {
        // Block color based on type
        Color fillColor;
        if (block.getIndex() == 0) {
            fillColor = Color.web("#f39c12"); // Gold for genesis
        } else if (block.getIndex() == blockchain.size() - 1) {
            fillColor = Color.web("#27ae60"); // Green for latest
        } else {
            fillColor = Color.web("#3498db"); // Blue for normal
        }

        // Shadow
        gc.setFill(Color.web("#00000033"));
        gc.fillRoundRect(x + 3, y + 3, BLOCK_WIDTH, BLOCK_HEIGHT, 10, 10);

        // Block body
        gc.setFill(fillColor);
        gc.fillRoundRect(x, y, BLOCK_WIDTH, BLOCK_HEIGHT, 10, 10);

        // Border
        gc.setStroke(Color.web("#2c3e50"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, BLOCK_WIDTH, BLOCK_HEIGHT, 10, 10);

        // Text
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 14));

        String indexText = "Block #" + block.getIndex();
        gc.fillText(indexText, x + 10, y + 25);

        gc.setFont(Font.font("Arial", 10));
        String hashText = block.getHash().substring(0, 8) + "...";
        gc.fillText("Hash: " + hashText, x + 10, y + 45);

        String nonceText = "Nonce: " + block.getNonce();
        gc.fillText(nonceText, x + 10, y + 60);
    }

    private void handleMouseMove(MouseEvent event) {
        Block block = getBlockAt(event.getX(), event.getY());
        if (block != null) {
            String tooltipText = String.format(
                    "Block #%d\nHash: %s\nPrev: %s\nNonce: %d\nData: %s",
                    block.getIndex(),
                    block.getHash(),
                    block.getPreviousHash().substring(0, Math.min(16, block.getPreviousHash().length())),
                    block.getNonce(),
                    block.getData()
            );
            tooltip.setText(tooltipText);
            Tooltip.install(canvas, tooltip);
        } else {
            Tooltip.uninstall(canvas, tooltip);
        }
    }

    private void handleMouseClick(MouseEvent event) {
        Block block = getBlockAt(event.getX(), event.getY());
        if (block != null) {
            showBlockDetails(block);
        }
    }

    private Block getBlockAt(double mouseX, double mouseY) {
        int y = 60;
        for (int i = 0; i < blockchain.size(); i++) {
            int x = PADDING + i * (BLOCK_WIDTH + BLOCK_SPACING);
            if (mouseX >= x && mouseX <= x + BLOCK_WIDTH &&
                    mouseY >= y && mouseY <= y + BLOCK_HEIGHT) {
                return blockchain.getBlock(i);
            }
        }
        return null;
    }

    private void showBlockDetails(Block block) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
        );
        alert.setTitle("Block Details");
        alert.setHeaderText("Block #" + block.getIndex());
        alert.setContentText(
                "Hash: " + block.getHash() + "\n\n" +
                        "Previous Hash: " + block.getPreviousHash() + "\n\n" +
                        "Nonce: " + block.getNonce() + "\n\n" +
                        "Timestamp: " + block.getTimestamp() + "\n\n" +
                        "Data: " + block.getData()
        );
        alert.showAndWait();
    }
}
