package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ActivePortForwardService;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * 后台活跃端口转发管理窗口。
 *
 * <p>列出所有正在后台运行的转发，支持单条停止和全部停止。关闭窗口不影响转发运行。</p>
 */
public final class ActiveForwardsWindow {

    private final Stage stage;
    private final ActivePortForwardService activeForwardService;
    private final TableView<ActivePortForwardService.ActiveForwardInfo> table = new TableView<>();
    private final ObservableList<ActivePortForwardService.ActiveForwardInfo> rows = FXCollections.observableArrayList();

    public ActiveForwardsWindow(ActivePortForwardService activeForwardService, Window owner) {
        this.activeForwardService = activeForwardService;

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.t("forward.active.title"));
        stage.setScene(new Scene(buildRoot(), 680, 320));
    }

    public void show() {
        refresh();
        stage.show();
    }

    private BorderPane buildRoot() {
        Button stop = new Button(I18n.t("forward.active.stop"));
        stop.setOnAction(e -> stopSelected());
        Button stopAll = new Button(I18n.t("forward.active.stop_all"));
        stopAll.setOnAction(e -> stopAll());
        Button refresh = new Button(I18n.t("forward.active.refresh"));
        refresh.setOnAction(e -> refresh());
        ToolBar toolBar = new ToolBar(stop, stopAll, refresh);

        buildTable();

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        VBox center = new VBox(table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        center.setPadding(new Insets(8));
        root.setCenter(center);
        return root;
    }

    private void buildTable() {
        TableColumn<ActivePortForwardService.ActiveForwardInfo, String> connCol =
                new TableColumn<>(I18n.t("forward.active.column.connection"));
        connCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().connectionName()));
        connCol.setPrefWidth(130);

        TableColumn<ActivePortForwardService.ActiveForwardInfo, String> nameCol =
                new TableColumn<>(I18n.t("forward.column.name"));
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().ruleName()));
        nameCol.setPrefWidth(110);

        TableColumn<ActivePortForwardService.ActiveForwardInfo, String> typeCol =
                new TableColumn<>(I18n.t("forward.column.type"));
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(I18n.t(typeKey(cd.getValue().type()))));
        typeCol.setPrefWidth(90);

        TableColumn<ActivePortForwardService.ActiveForwardInfo, String> bindCol =
                new TableColumn<>(I18n.t("forward.column.bind"));
        bindCol.setCellValueFactory(cd -> {
            ActivePortForwardService.ActiveForwardInfo info = cd.getValue();
            return new ReadOnlyStringWrapper(info.bindHost() + ":" + info.bindPort());
        });
        bindCol.setPrefWidth(130);

        TableColumn<ActivePortForwardService.ActiveForwardInfo, String> targetCol =
                new TableColumn<>(I18n.t("forward.column.target"));
        targetCol.setCellValueFactory(cd -> {
            ActivePortForwardService.ActiveForwardInfo info = cd.getValue();
            String text = info.type() == PortForwardSpec.Type.DYNAMIC
                    ? "-"
                    : info.destHost() + ":" + info.destPort();
            return new ReadOnlyStringWrapper(text);
        });
        targetCol.setPrefWidth(140);

        table.getColumns().add(connCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(bindCol);
        table.getColumns().add(targetCol);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(I18n.t("forward.active.empty")));
    }

    private void refresh() {
        List<ActivePortForwardService.ActiveForwardInfo> active = activeForwardService.getActiveForwards();
        Platform.runLater(() -> {
            rows.setAll(active);
            table.refresh();
        });
    }

    private void stopSelected() {
        ActivePortForwardService.ActiveForwardInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        activeForwardService.stopForward(selected.connectionId(), selected.ruleName());
        refresh();
    }

    private void stopAll() {
        activeForwardService.stopAll();
        refresh();
    }

    private static String typeKey(PortForwardSpec.Type t) {
        return switch (t) {
            case LOCAL -> "forward.type.local";
            case REMOTE -> "forward.type.remote";
            case DYNAMIC -> "forward.type.dynamic";
        };
    }
}
