package com.testbird.util.codeanalyser;

import com.testbird.util.common.JsonTransfer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;

/**
 * Created by jiayinxi on 17/5/27.
 */
public class Controller implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    @FXML
    private Pane root;

    private Stage primaryStage;
    private Dialog dialog = new Dialog();
    static final double rootWidth = 1220;
    static final double rootHeight = 750;
    private static final double headerHeight = 64;
    private static final double bodyHeight = rootHeight - headerHeight;
    private static final double leftPaneWidth = 200;
    private static final double rightPaneWidth = rootWidth - 200;

    private static final Image logoImg = new Image("/javafx/logo_white.png");
    private Canvas headerCanvas;
    private static final String KEYWORDS_ANALYSIS = "敏感词安全扫描";
    private ObservableList<String> leftMenuItems = FXCollections.observableArrayList(
            "用例管理", "自动回归测试", "兼容性测试", KEYWORDS_ANALYSIS);

    private ListView<String> listViewLeftMenu = new ListView<>(leftMenuItems);
    private Pane blankPane = new Pane();

    // keywords management controls
    private GridPane gridPaneKeywordManagement;
    TextField textFieldKeyword;

    private ObservableList<String> keywordsList;
    private ListView listViewKeywords;

    private TabPane keywordsTabPane = new TabPane();
    // private ScrollPane keywordsSrcRepoRootPane = new ScrollPane();
    // private ScrollPane keywordsSrcPkgRootPane = new ScrollPane();
    // private GridPane keywordsSrcRepoPane = new GridPane();
    private HashMap<String, AnalyseResult> srcRepoAnalyseResult = new HashMap<>();
    private HashMap<String, AnalyseResult> srcFileAnalyseResult = new HashMap<>();
    // private GridPane keywordsSrcPkgPane = new GridPane();

    void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    class AnalyseResult {
        AnalyseResult(String repoLocation) {
            start = System.currentTimeMillis();
            this.repoLocation = repoLocation;
        }
        long start; // ms
        long end;
        String repoLocation;
        List<KeywordMatchInfo> keywordMatchInfoList;
    }

    public void stageSizeChanged() {
        // repaint header
        headerCanvas.setWidth(primaryStage.getWidth());
        GraphicsContext gc = headerCanvas.getGraphicsContext2D();
        gc.setFill(Paint.valueOf("#0070f0")); // #0070f0;
        gc.fillRect(0, 0, primaryStage.getWidth(), headerHeight);

        // resize left pane
        listViewLeftMenu.setLayoutX(0);
        listViewLeftMenu.setLayoutY(headerHeight);
        listViewLeftMenu.setPrefWidth(leftPaneWidth);
        listViewLeftMenu.setPrefHeight(primaryStage.getHeight() - headerHeight);

        // layout keywords management controls
        // fixed height
        gridPaneKeywordManagement.setPrefWidth(primaryStage.getWidth() - leftPaneWidth);

        // tabs
        resizeTabPane();
    }

    private void resizeTabPane() {
        keywordsTabPane.setPrefWidth(primaryStage.getWidth() - leftPaneWidth);
        keywordsTabPane.setPrefHeight(primaryStage.getHeight() - gridPaneKeywordManagement.getPrefHeight() - headerHeight);
        List<Tab> tabs = keywordsTabPane.getTabs();

    }

    // This method is called by the FXMLLoader when initialization is complete
    @Override
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        root.setPrefWidth(rootWidth);
        root.setPrefHeight(rootHeight);

        blankPane.getChildren().add(new Label("TO DO..."));
        blankPane.setLayoutX(leftPaneWidth);
        blankPane.setLayoutY(headerHeight);
        root.getChildren().add(blankPane);

        initHeader();
        initKeywordsAnalysePane();
        initLeftPaneListView();
        // StackPane sp2 = new StackPane();
        // // sp2.getChildren().add(new Button("Button Two"));
        // sp.getItems().addAll(sp1, sp2);
    }

    private void initHeader() {
        headerCanvas = new Canvas(rootWidth, headerHeight);
        GraphicsContext gc = headerCanvas.getGraphicsContext2D();
        gc.setFill(Paint.valueOf("#0070f0")); // #0070f0;
        gc.fillRect(0, 0, rootWidth, headerHeight);
        ImageView logoImgView = new ImageView();
        logoImgView.setPreserveRatio(true);
        logoImgView.setFitWidth(90); // 153
        logoImgView.setFitHeight(34); // 58
        logoImgView.setImage(logoImg);
        logoImgView.setLayoutX(5);
        logoImgView.setLayoutY(32 - 17);
        root.getChildren().addAll(headerCanvas, logoImgView);
    }

    private void initLeftPaneListView() {
        listViewLeftMenu.setLayoutX(0);
        listViewLeftMenu.setLayoutY(headerHeight);
        listViewLeftMenu.setPrefWidth(leftPaneWidth);
        listViewLeftMenu.setPrefHeight(bodyHeight);
        listViewLeftMenu.getSelectionModel().selectedItemProperty().addListener((ov, oldVal, newVal) -> {
            String item = listViewLeftMenu.getSelectionModel().getSelectedItem();
            if (item.equals(KEYWORDS_ANALYSIS)) {
                gridPaneKeywordManagement.setVisible(true);
                keywordsTabPane.setVisible(true);
                blankPane.setVisible(false);
            } else {
                blankPane.setVisible(true);
                keywordsTabPane.setVisible(false);
                gridPaneKeywordManagement.setVisible(false);
            }
        });
        listViewLeftMenu.getSelectionModel().select(KEYWORDS_ANALYSIS);

        root.getChildren().add(listViewLeftMenu);


    }

    private void addKeyword() {
        String keyword = textFieldKeyword.getText();
        if (keyword.trim().isEmpty()) {
            return;
        }
        if (!keywordsList.contains(keyword)) {
            keywordsList.add(keyword);
            textFieldKeyword.clear();
        }
    }

    private void initKeywordsManagementPane() {
        // keywords management
        long keywordsListHeight = 300;
        long gridCellWidth = 100;
        int gridColumnCnt = 9;
        double gridPaneWidth = rightPaneWidth - 20; // 1000
        gridPaneKeywordManagement = new GridPane();
        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(80);
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(20);
        gridPaneKeywordManagement.getColumnConstraints().addAll(column1, column2);

        gridPaneKeywordManagement.setLayoutX(leftPaneWidth + 10);
        gridPaneKeywordManagement.setLayoutY(headerHeight);
        gridPaneKeywordManagement.setVgap(10);
        gridPaneKeywordManagement.setHgap(15);
        gridPaneKeywordManagement.setPrefWidth(rightPaneWidth);
        gridPaneKeywordManagement.setPrefHeight(keywordsListHeight);

        textFieldKeyword = new TextField();
        textFieldKeyword.setOnKeyPressed( event -> {
            KeyCode keyCode = event.getCode();
            if (KeyCode.ENTER == keyCode) {
                addKeyword();
            }
        });
        gridPaneKeywordManagement.add(textFieldKeyword, 0, 1);

        CheckBox checkBoxRegExp = new CheckBox("正则匹配");
        gridPaneKeywordManagement.add(checkBoxRegExp, 1, 1);

        keywordsList = FXCollections.observableArrayList();
        listViewKeywords = new ListView(keywordsList);
        listViewKeywords.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        gridPaneKeywordManagement.add(listViewKeywords, 0, 2, 1, 5);

        Button btnAddKeyword = new Button("添加敏感词");
        Button btnRemoveKeyword = new Button("删除敏感词");
        btnAddKeyword.setOnMouseClicked(event -> {
            addKeyword();
        });
        btnRemoveKeyword.setOnMouseClicked(event -> {
            ObservableList<String> items = listViewKeywords.getSelectionModel().getSelectedItems();
            listViewKeywords.getItems().removeAll(items);
            // for (String item : items) listViewKeywords.getItems().remove(item);
        });
        gridPaneKeywordManagement.add(btnAddKeyword, 1, 2);
        gridPaneKeywordManagement.add(btnRemoveKeyword, 1, 3);
    }

    class AnalyseResultCell extends ListCell<String> {
        HashMap<String, AnalyseResult> results;
        AnalyseResultCell(HashMap<String, AnalyseResult> results) {
            this.results = results;
        }
        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (!empty) {
                AnalyseResult result = results.get(item);
                VBox vBoxResult = new VBox();
                Label labelStatus = new Label("正在扫描 " + item + " ...");
                vBoxResult.getChildren().add(labelStatus);
                if (null != result.keywordMatchInfoList) {
                    labelStatus.setText(item + " 扫描完成，耗时：" + (result.end - result.start)/1000 + "秒");
                    TextArea textAreaResult = new TextArea();
                    textAreaResult.setWrapText(true);
                    textAreaResult.setText(JsonTransfer.toJsonFormatString(result.keywordMatchInfoList));
                    vBoxResult.getChildren().add(textAreaResult);
                } else {
                    Button btnStop = new Button("停止扫描");
                    vBoxResult.getChildren().add(btnStop);
                }
                setGraphic(vBoxResult);
            }
        }
    }

    private Tab initSrcRepoTab() {
        //////////////////////////////////////////////
        // src repo tab
        Tab tab = new Tab("扫描代码库");

        // keywordsSrcRepoRootPane.setLayoutX(leftPaneWidth);
        // keywordsSrcRepoRootPane.setLayoutY(headerHeight);
        // keywordsSrcRepoRootPane.setPrefWidth(rightPaneWidth);
        // keywordsSrcRepoRootPane.setPrefHeight(bodyHeight);

        // keywordsSrcRepoRootPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Horizontal
        // keywordsSrcRepoRootPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Vertical scroll bar
        // keywordsSrcRepoRootPane.setFitToWidth(true);

        BorderPane borderPane = new BorderPane();
        VBox tabRootPane = new VBox();
        borderPane.setCenter(tabRootPane);
        borderPane.setMargin(tabRootPane, new Insets(20, 20, 20,20));

        tabRootPane.setLayoutX(20);
        tabRootPane.setPrefWidth(rootWidth - leftPaneWidth);
        // tabRootPane.setPrefHeight(rootHeight - headerHeight - );
        tabRootPane.setSpacing(15);
        tabRootPane.setAlignment(Pos.TOP_CENTER);

        // keywordsSrcRepoPane.setVgap(20);
        // keywordsSrcRepoPane.setAlignment(Pos.CENTER);

        HBox hBoxSrcRepo1 = new HBox();
        hBoxSrcRepo1.setPrefWidth(rootWidth - leftPaneWidth - 20);
        hBoxSrcRepo1.setSpacing(10);
        hBoxSrcRepo1.setAlignment(Pos.CENTER_LEFT);
        ComboBox comboBoxRepoType = new ComboBox();
        comboBoxRepoType.getItems().addAll("git", "svn");
        comboBoxRepoType.setValue("git");

        Label labelUserName = new Label("用户名：");
        TextField textFieldUserName = new TextField();
        Label labelPassword = new Label("密码：");
        PasswordField passwordField = new PasswordField();
        hBoxSrcRepo1.getChildren().addAll(comboBoxRepoType, labelUserName, textFieldUserName, labelPassword, passwordField);

        HBox hBoxSrcRepo2 = new HBox();
        hBoxSrcRepo2.setPrefWidth(rootWidth - leftPaneWidth - 20);
        hBoxSrcRepo2.setSpacing(10);
        hBoxSrcRepo2.setAlignment(Pos.CENTER_LEFT);

        Label labelSrcRepoAddr = new Label("代码库地址：");
        Button btnAnalyseSrcRepo = new Button("开始扫描");
        final TextField textFieldRepoAddr = new TextField();
        textFieldRepoAddr.setPrefWidth(rightPaneWidth * 0.7);

        ListView<String> listViewAnalyseResults = new ListView<>();
        listViewAnalyseResults.setPrefWidth(rootWidth - leftPaneWidth - 20);
        listViewAnalyseResults.setPrefHeight(rootHeight - (headerHeight + gridPaneKeywordManagement.getPrefHeight() + 200));
        ObservableList<String> analyseResultList = FXCollections.observableArrayList();
        listViewAnalyseResults.setItems(analyseResultList);

        listViewAnalyseResults.setCellFactory(listViewResults -> new AnalyseResultCell(srcRepoAnalyseResult));

        btnAnalyseSrcRepo.setOnMouseClicked(event -> {
            String userName = textFieldUserName.getText().trim();
            String password = passwordField.getText();
            String repoUrl = textFieldRepoAddr.getText();
            if (StringUtils.equalsAny("", userName, password, repoUrl)) {
                showDialog("Info", "请填写代码库信息。");
                return;
            }
            if (listViewKeywords.getItems().size() <= 0) {
                showDialog("Info", "请添加敏感词。");
                return;
            }

            AnalyseResult result = new AnalyseResult(repoUrl);
            srcRepoAnalyseResult.put(repoUrl, result);
            analyseResultList.add(repoUrl);
            new Thread(() -> {
                try {
                    // List<KeyWordInfo> keyWordInfoList = SrcCodeAnalyser.findKeywordsInSrcRepo("git", repoUrl, userName, password, listViewKeywords.getItems());
                    result.end = System.currentTimeMillis();
                    // result.keyWordInfoList = keyWordInfoList;
                    System.out.println("Finished scanning.");
                    Platform.runLater(() -> {
                        listViewAnalyseResults.refresh();
                    });
                } catch (Exception e) {
                    showDialog("Error", e.getMessage());
                }
            }).start();
            // showDialog("Result", JsonTransfer.toJsonFormatString(keyWordInfoList));
        });
        hBoxSrcRepo2.getChildren().addAll(labelSrcRepoAddr, textFieldRepoAddr, btnAnalyseSrcRepo);
        double listViewHeight = tabRootPane.getPrefHeight() - hBoxSrcRepo1.getHeight() - hBoxSrcRepo2.getHeight();
        LOGGER.info("tab root pane height: {}, hbox1 height:{}, 2 height: {} result list view height: {}", tabRootPane.getPrefHeight(), hBoxSrcRepo1.getHeight(), hBoxSrcRepo2.getHeight(), listViewHeight);
        // listViewAnalyseResults.setPrefHeight(tabRootPane.getPrefHeight() - hBoxSrcRepo1.getHeight() - hBoxSrcRepo2.getHeight());
        tabRootPane.getChildren().addAll(hBoxSrcRepo1, hBoxSrcRepo2, listViewAnalyseResults);
        // keywordsSrcRepoPane.add(hBoxSrcRepo1, 0, 0);
        // keywordsSrcRepoPane.add(hBoxSrcRepo2, 0, 1);
        // keywordsSrcRepoPane.add(listViewAnalyseResults, 0, 2);

        // keywordsSrcRepoRootPane.setContent(keywordsSrcRepoPane);
        tab.setContent(borderPane);
        return tab;
    }

    private void resizeSrcRepoPane() {

    }

    private Tab initLocalSrcTab() {
        Tab tab = new Tab("扫描本地代码");
        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected()) {
                // gridPaneKeywordManagement.setVisible(true);
            }
        });

        ///
        // keywordsSrcPkgRootPane.setLayoutX(leftPaneWidth);
        // keywordsSrcPkgRootPane.setLayoutY(headerHeight);
        // keywordsSrcPkgRootPane.setPrefWidth(rightPaneWidth);
        // keywordsSrcPkgRootPane.setPrefHeight(bodyHeight);

        // keywordsSrcPkgRootPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Horizontal
        // keywordsSrcPkgRootPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Vertical scroll bar
        // keywordsSrcPkgRootPane.setFitToWidth(true);
        BorderPane borderPane = new BorderPane();
        VBox tabRootPane = new VBox();
        borderPane.setCenter(tabRootPane);
        borderPane.setMargin(tabRootPane, new Insets(20, 20, 20,20));

        tabRootPane.setLayoutX(20);
        tabRootPane.setPrefWidth(rootWidth - leftPaneWidth);
        tabRootPane.setSpacing(15);
        tabRootPane.setAlignment(Pos.TOP_CENTER);

        // keywordsSrcPkgPane.setHgap(15);
        // keywordsSrcPkgPane.setVgap(20);
        // keywordsSrcPkgPane.setAlignment(Pos.CENTER);

        HBox hBoxSrcPath1 = new HBox();
        hBoxSrcPath1.setPrefWidth(rootWidth - leftPaneWidth - 20);
        hBoxSrcPath1.setSpacing(10);
        hBoxSrcPath1.setAlignment(Pos.CENTER_LEFT);

        Label labelSrcPath = new Label("源码包或目录：");
        TextField textFieldSrcPath = new TextField();
        textFieldSrcPath.setPrefWidth(rightPaneWidth * 0.7);
        Button btnAnalyseSrcPath = new Button("开始扫描");
        hBoxSrcPath1.getChildren().addAll(labelSrcPath, textFieldSrcPath, btnAnalyseSrcPath);

        ListView<String> listViewAnalyseResults = new ListView<>();
        listViewAnalyseResults.setPrefWidth(rootWidth - leftPaneWidth - 20);
        listViewAnalyseResults.setPrefHeight(rootHeight - (headerHeight + gridPaneKeywordManagement.getPrefHeight() + 200));
        ObservableList<String> analyseResultList = FXCollections.observableArrayList();
        listViewAnalyseResults.setItems(analyseResultList);

        listViewAnalyseResults.setCellFactory(listViewResults -> new AnalyseResultCell(srcFileAnalyseResult));

        btnAnalyseSrcPath.setOnMouseClicked(event -> {
            String localSrcPath = textFieldSrcPath.getText().trim();
            if (StringUtils.isEmpty(localSrcPath)) {
                showDialog("Info", "请填写代码路径。");
                return;
            }
            if (listViewKeywords.getItems().size() <= 0) {
                showDialog("Info", "请添加敏感词。");
                return;
            }
            AnalyseResult result = new AnalyseResult(localSrcPath);
            srcFileAnalyseResult.put(localSrcPath, result);
            analyseResultList.add(localSrcPath);
            new Thread(() -> {
                try {
                    // SearchKeywordsResult searcResult =  SrcCodeAnalyser.searchKeywordsInDir(localSrcPath, listViewKeywords.getItems(), null, null,true);
                    // result.end = System.currentTimeMillis();
                    // result.keywordMatchInfoList = searcResult.keywordMatchInfos;
                    // System.out.println("Finished scanning.");
                    // Platform.runLater(() -> {
                    //     listViewAnalyseResults.refresh();
                    // });
                } catch (Exception e) {
                    showDialog("Error", e.getMessage());
                    return;
                }
            }).start();
        });
        // keywordsSrcPkgPane.add(hBoxSrcPath1, 0, 0);
        // keywordsSrcPkgPane.add(listViewAnalyseResults, 0, 1);


        // keywordsSrcPkgRootPane.setContent(keywordsSrcPkgPane);
        tabRootPane.getChildren().addAll(hBoxSrcPath1, listViewAnalyseResults);
        tab.setContent(borderPane);
        return tab;
    }

    private void initKeywordsAnalysePane() {
        initKeywordsManagementPane();

        keywordsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        keywordsTabPane.setLayoutX(leftPaneWidth);
        keywordsTabPane.setLayoutY(headerHeight + gridPaneKeywordManagement.getPrefHeight());
        keywordsTabPane.setPrefWidth(rightPaneWidth);
        keywordsTabPane.setPrefHeight(bodyHeight);

        Tab tabSrcRepo = initSrcRepoTab();
        Tab tabLocalSrc = initLocalSrcTab();
        keywordsTabPane.getTabs().addAll(tabSrcRepo, tabLocalSrc);

        root.getChildren().addAll(gridPaneKeywordManagement, keywordsTabPane);
    }

    public void onCloseRequest() {
        LOGGER.info("onCloseRequest");
    }
    private void showDialog(String title, String content) {
        Platform.runLater(() -> dialog.display(title, content));
    }
}
