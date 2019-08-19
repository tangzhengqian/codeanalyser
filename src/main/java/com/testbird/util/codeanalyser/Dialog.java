package com.testbird.util.codeanalyser;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by jiayinxi on 2017/5/31.
 */
public class Dialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dialog.class);
    private static final String ID_CHOOSE_BTN = "ID_CHOOSE_BTN";
    private static final String ID_OK_BTN = "ID_OK_BTN";
    private static final String ID_CANCEL_BTN = "ID_CANCEL_BTN";

    public void display(String title, String content){
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);

        Label label = new Label();
        label.setText(content);
        Button okBtn = new Button();
        okBtn.setText("ok");
        okBtn.setMinWidth(100);
        okBtn.setPrefWidth(100);
        okBtn.setMaxWidth(100);

        okBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                stage.close();
            }
        });

        VBox layout = new VBox();
        layout.setMinWidth(300);
        layout.setMinHeight(150);
        layout.getChildren().addAll(label, okBtn);
        layout.setMargin(label, new Insets(20));
        layout.setMargin(okBtn, new Insets(20));
        layout.setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout);
        stage.setScene(scene);
        stage.showAndWait();
    }

}
