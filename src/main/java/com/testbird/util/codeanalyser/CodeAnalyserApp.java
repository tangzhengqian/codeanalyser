package com.testbird.util.codeanalyser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.beans.value.ChangeListener;


public class CodeAnalyserApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/javafx/codeanalyser.fxml"));
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(Controller.rootWidth);
        primaryStage.setMinHeight(Controller.rootHeight);
        primaryStage.setWidth(Controller.rootWidth);
        primaryStage.setHeight(Controller.rootHeight);
        primaryStage.setTitle("Code Analyser");

        Parent root = fxmlLoader.load();
        Controller controller = fxmlLoader.getController();
        controller.setPrimaryStage(primaryStage);

        // void changed(ObservableValue<? extends T> observable, T oldValue, T newValue);
        ChangeListener changeListener = (observable, oldValue, newValue) -> {
            controller.stageSizeChanged();
        };
        primaryStage.widthProperty().addListener(changeListener);
        primaryStage.heightProperty().addListener(changeListener);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> controller.onCloseRequest());
        primaryStage.show();
    }

    public static void go(String[] args) {
        launch(args);
    }
}
