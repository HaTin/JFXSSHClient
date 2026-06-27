package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ActivePortForwardService;
import com.xxx.jfxssh.service.PortForwardService;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 端口转发管理窗口（每个连接一个，独立窗口）。
 *
 * <p>规则表格（名称 / 类型 / 绑定 / 目标 / 状态）+ 工具栏（添加 / 启动 / 停止 / 编辑 / 移除 / 全部启动）。
 * 规则持久化到 SQLite，打开窗口时只加载不自动启动。启停委托给 {@link ActivePortForwardService} 在后台执行；
 * 关闭本窗口不会停止已启动的转发。</p>
 */
public final class PortForwardWindow {

    private static final Logger log = LoggerFactory.getLogger(PortForwardWindow.class);

    /** 行状态。 */
    private enum Status { STOPPED, STARTING, RUNNING, ERROR }

    /** 表格行模型（可变，状态变化后调用 {@code table.refresh()}）。 */
    private static final class Row {
        private final Long ruleId;
        private PortForwardSpec spec;
        private Status status = Status.STOPPED;
        private String detail = "";

        Row(Long ruleId, PortForwardSpec spec) {
            this.ruleId = ruleId;
            this.spec = spec;
        }
    }

    private final Stage stage;
    private final Connection connection;
    private final SshConnectionConfig sshConfig;
    private final PortForwardService portForwardService;
    private final ActivePortForwardService activeForwardService;
    private final ExecutorService executor;
    private final Consumer<PortForwardWindow> onClose;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private Button startButton;
    private Button startAllButton;
    private Button stopButton;
    private Button editButton;
    private Button removeButton;

    /**
     * @param title              连接显示名（窗口标题）
     * @param connection         所属连接（用于持久化规则）
     * @param sshConfig          SSH 连接配置（启动转发用）
     * @param portForwardService 端口转发规则持久化服务
     * @param activeForwardService 后台转发服务
     * @param owner              父窗口（可空）
     * @param onClose            关闭回调（从启动器注册表移除）
     */
    public PortForwardWindow(String title,
                             Connection connection,
                             SshConnectionConfig sshConfig,
                             PortForwardService portForwardService,
                             ActivePortForwardService activeForwardService,
                             Window owner,
                             Consumer<PortForwardWindow> onClose) {
        this.connection = connection;
        this.sshConfig = sshConfig;
        this.portForwardService = portForwardService;
        this.activeForwardService = activeForwardService;
        this.onClose = onClose;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "forward-ops-" + title);
            t.setDaemon(true);
            return t;
        });

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.t("forward.title", title));
        stage.setScene(new Scene(buildRoot(), 720, 420));
        stage.setOnCloseRequest(e -> close());
    }

    /** 显示窗口并加载已保存的规则（不自动启动）。 */
    public void show() {
        loadRules();
        stage.show();
    }

    /** 关闭窗口：仅关闭 UI，不停止后台转发（幂等）。 */
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        if (onClose != null) {
            onClose.accept(this);
        }
        if (stage.isShowing()) {
            stage.close();
        }
    }

    private BorderPane buildRoot() {
        Button add = new Button(I18n.t("forward.button.add"));
        add.setOnAction(e -> addRule());
        startButton = new Button(I18n.t("forward.button.start"));
        startButton.setOnAction(e -> startSelected());
        stopButton = new Button(I18n.t("forward.button.stop"));
        stopButton.setOnAction(e -> stopSelected());
        editButton = new Button(I18n.t("forward.button.edit"));
        editButton.setOnAction(e -> editSelected());
        removeButton = new Button(I18n.t("forward.button.remove"));
        removeButton.setOnAction(e -> removeSelected());
        startAllButton = new Button(I18n.t("forward.button.start_all"));
        startAllButton.setOnAction(e -> startAll());
        ToolBar toolBar = new ToolBar(add, startButton, stopButton, editButton, removeButton, startAllButton);

        buildTable();
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtonStates());
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                editSelected();
            }
        });
        updateButtonStates();

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        VBox center = new VBox(table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        center.setPadding(new Insets(8));
        root.setCenter(center);
        return root;
    }

    private void updateButtonStates() {
        Row selected = table.getSelectionModel().getSelectedItem();
        boolean running = selected != null && selected.status == Status.RUNNING;
        boolean canStart = selected != null
                && selected.status != Status.RUNNING
                && selected.status != Status.STARTING;
        boolean canEdit = selected != null
                && (selected.status == Status.STOPPED || selected.status == Status.ERROR);
        boolean canStartAny = rows.stream().anyMatch(r -> r.status != Status.RUNNING && r.status != Status.STARTING);
        startButton.setDisable(!canStart);
        stopButton.setDisable(!running);
        editButton.setDisable(!canEdit);
        removeButton.setDisable(selected == null);
        startAllButton.setDisable(!canStartAny);
    }

    private void buildTable() {
        TableColumn<Row, String> nameCol = new TableColumn<>(I18n.t("forward.column.name"));
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().spec.name()));
        nameCol.setPrefWidth(120);

        TableColumn<Row, String> typeCol = new TableColumn<>(I18n.t("forward.column.type"));
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(I18n.t(typeKey(cd.getValue().spec.type()))));
        typeCol.setPrefWidth(90);

        TableColumn<Row, String> bindCol = new TableColumn<>(I18n.t("forward.column.bind"));
        bindCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(bindText(cd.getValue())));
        bindCol.setPrefWidth(150);

        TableColumn<Row, String> targetCol = new TableColumn<>(I18n.t("forward.column.target"));
        targetCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(targetText(cd.getValue())));
        targetCol.setPrefWidth(160);

        TableColumn<Row, String> statusCol = new TableColumn<>(I18n.t("forward.column.status"));
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(statusText(cd.getValue())));
        statusCol.setPrefWidth(170);

        table.getColumns().add(nameCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(bindCol);
        table.getColumns().add(targetCol);
        table.getColumns().add(statusCol);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    /** 从数据库与后台服务加载当前连接的转发规则，合并状态。 */
    private void loadRules() {
        rows.clear();
        Map<String, ActivePortForwardService.ActiveForwardInfo> activeByName = new HashMap<>();
        for (ActivePortForwardService.ActiveForwardInfo info : activeForwardService.getActiveForwards(connection.getId())) {
            activeByName.put(info.ruleName(), info);
        }

        for (PortForwardRule rule : portForwardService.findByConnection(connection.getId())) {
            PortForwardSpec spec = new PortForwardSpec(
                    rule.getName(),
                    rule.getType(),
                    rule.getBindHost(),
                    rule.getBindPort(),
                    rule.getDestHost() == null ? "" : rule.getDestHost(),
                    rule.getDestPort());
            Row row = new Row(rule.getId(), spec);
            ActivePortForwardService.ActiveForwardInfo active = activeByName.get(spec.name());
            if (active != null) {
                row.status = Status.RUNNING;
                row.detail = String.valueOf(active.bindPort());
            }
            rows.add(row);
        }
        table.refresh();
        updateButtonStates();
    }

    private void addRule() {
        Optional<PortForwardSpec> spec = new PortForwardDialog().showAndWait();
        if (spec.isEmpty()) {
            return;
        }
        PortForwardRule rule = toEntity(spec.get());
        PortForwardRule saved = portForwardService.save(rule);
        rows.add(new Row(saved.getId(), spec.get()));
        table.refresh();
        updateButtonStates();
    }

    private void startSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.status != Status.RUNNING && row.status != Status.STARTING) {
            startRow(row);
        }
    }

    private void editSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null || (row.status != Status.STOPPED && row.status != Status.ERROR)) {
            return;
        }
        Optional<PortForwardSpec> spec = new PortForwardDialog(row.spec).showAndWait();
        if (spec.isEmpty()) {
            return;
        }
        PortForwardRule rule = toEntity(spec.get());
        rule.setId(row.ruleId);
        portForwardService.update(rule);
        row.spec = spec.get();
        row.status = Status.STOPPED;
        row.detail = "";
        table.refresh();
        updateButtonStates();
    }

    private void startAll() {
        for (Row row : rows) {
            if (row.status != Status.RUNNING && row.status != Status.STARTING) {
                startRow(row);
            }
        }
    }

    private void stopSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.status == Status.RUNNING) {
            stopRow(row);
        }
    }

    private void removeSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        if (row.status == Status.RUNNING) {
            activeForwardService.stopForward(connection.getId(), row.spec.name());
        }
        if (row.ruleId != null) {
            portForwardService.delete(row.ruleId);
        }
        rows.remove(row);
        table.refresh();
        updateButtonStates();
    }

    private void startRow(Row row) {
        row.status = Status.STARTING;
        row.detail = "";
        table.refresh();
        updateButtonStates();
        executor.submit(() -> {
            try {
                int boundPort = activeForwardService.startForward(connection, sshConfig, row.spec);
                Platform.runLater(() -> {
                    row.status = Status.RUNNING;
                    row.detail = String.valueOf(boundPort);
                    table.refresh();
                    updateButtonStates();
                });
            } catch (RuntimeException ex) {
                log.warn("Start forward failed for {}: {}", row.spec.name(), ex.getMessage());
                Platform.runLater(() -> {
                    row.status = Status.ERROR;
                    row.detail = ex.getMessage();
                    table.refresh();
                    updateButtonStates();
                });
            }
        });
    }

    private void stopRow(Row row) {
        row.status = Status.STOPPED;
        row.detail = "";
        table.refresh();
        updateButtonStates();
        executor.submit(() -> activeForwardService.stopForward(connection.getId(), row.spec.name()));
    }

    private PortForwardRule toEntity(PortForwardSpec spec) {
        PortForwardRule rule = new PortForwardRule();
        rule.setConnectionId(connection.getId());
        rule.setName(spec.name());
        rule.setType(spec.type());
        rule.setBindHost(spec.bindHost());
        rule.setBindPort(spec.bindPort());
        if (spec.type() != PortForwardSpec.Type.DYNAMIC) {
            rule.setDestHost(spec.destHost());
            rule.setDestPort(spec.destPort());
        }
        return rule;
    }

    private String bindText(Row row) {
        PortForwardSpec spec = row.spec;
        int port = spec.bindPort();
        // 运行中且系统分配了临时端口时显示实际端口
        if (port == 0 && row.status == Status.RUNNING && !row.detail.isBlank()) {
            try {
                port = Integer.parseInt(row.detail);
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        String portText = port == 0 ? "auto" : String.valueOf(port);
        return spec.bindHost() + ":" + portText;
    }

    private String targetText(Row row) {
        PortForwardSpec spec = row.spec;
        if (spec.type() == PortForwardSpec.Type.DYNAMIC) {
            return "-";
        }
        return spec.destHost() + ":" + spec.destPort();
    }

    private String statusText(Row row) {
        return switch (row.status) {
            case STOPPED -> I18n.t("forward.status.stopped");
            case STARTING -> I18n.t("forward.status.starting");
            case RUNNING -> I18n.t("forward.status.running") + " (:" + row.detail + ")";
            case ERROR -> I18n.t("forward.status.error") + ": " + row.detail;
        };
    }

    private static String typeKey(PortForwardSpec.Type t) {
        return switch (t) {
            case LOCAL -> "forward.type.local";
            case REMOTE -> "forward.type.remote";
            case DYNAMIC -> "forward.type.dynamic";
        };
    }
}
