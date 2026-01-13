package com.study.blockchain.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point.
 */
public class App extends Application {

    private static int port = 8001;
    private static String connectTo = null;

    @Override
    public void start(Stage primaryStage) {
        MainController controller = new MainController(port);

        Scene scene = new Scene(controller.getRoot(), 1000, 700);
        primaryStage.setTitle("Blockchain Demo - Node " + port);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> controller.shutdown());
        primaryStage.show();

        controller.initialize();

        if (connectTo != null) {
            controller.connectToPeer(connectTo);
        }
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring(7));
            } else if (arg.startsWith("--connect=")) {
                connectTo = arg.substring(10);
            }
        }
        launch(args);
    }
}
