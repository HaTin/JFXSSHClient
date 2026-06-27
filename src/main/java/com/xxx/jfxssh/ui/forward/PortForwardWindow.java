package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.PortForward;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.ssh.SshSession;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 端口转发管理窗口（每个连接一个，独立窗口）。
 *
 * <p>规则表格（名称 / 类型 / 绑定 / 目标 / 状态）+ 工具栏（添加 / 启动 / 停止 / 移除）。
 * 规则仅内存保存，关窗即丢。启停经单线程执行器在后台执行，结果回 FX 线程刷新表格。
 * 关窗时停止所有转发并关闭底层 SSH 会话。</p>
 */
public final class PortForwardWindow {

    private static final Logger log = LoggerFactory.getLogger(PortForwardWindow.class);

    /** 行状态。 */
    private enum Status { STOPPED, STARTING, RUNNING, ERROR }

    /** 表格行模型（可变，状态变化后调用 {@code table.refresh()}）。 */
    private static final class Row {
        private final PortForwardSpec spec;
        private PortForward handle;
        private Status status = Status.STOPPED;
        private String detail = "";
        private volatile boolean stopPending;

        Row(PortForwardSpec spec) {
            this.spec = spec;
        }
    }

    private final Stage stage;
    private final SshSession ssh;
    private final ExecutorService executor;
    private final Consumer<PortForwardWindow> onClose;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private Button startButton;
    private Button stopButton;
    private Button removeButton;

    /**
     * @param title   连接显示名（窗口标题）
     * @param ssh     依附的 SSH 会话（窗口关闭时一并关闭）
     * @param owner   父窗口（可空）
     * @param onClose 关闭回调（从启动器注册表移除）
     */
    public PortForwardWindow(String title, SshSession ssh, Window owner, Consumer<PortForwardWindow> onClose) {
        this.ssh = ssh;
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

    /** 显示窗口。 */
    public void show() {
        stage.show();
    }

    /** 关闭窗口：停止所有转发并释放执行器与 SSH 会话（幂等）。 */
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        for (Row row : rows) {
            PortForward handle = row.handle;
            row.handle = null;
            if (handle != null) {
                try {
                    handle.close();
                } catch (RuntimeException e) {
                    log.warn("Error stopping forward {} on window close: {}", row.spec.name(), e.getMessage());
                }
            } else {
                // 启动中就被关闭窗口：让 startRow 完成后自行关闭
                row.stopPending = true;
            }
        }
        executor.shutdownNow();
        Thread closer = new Thread(ssh::close, "forward-close");
        closer.setDaemon(true);
        closer.start();
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
        removeButton = new Button(I18n.t("forward.button.remove"));
        removeButton.setOnAction(e -> removeSelected());
        ToolBar toolBar = new ToolBar(add, startButton, stopButton, removeButton);

        buildTable();
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtonStates());
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
        startButton.setDisable(!canStart);
        stopButton.setDisable(!running);
        removeButton.setDisable(selected == null);
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

    private void addRule() {
        Optional<PortForwardSpec> spec = new PortForwardDialog().showAndWait();
        if (spec.isEmpty()) {
            return;
        }
        Row row = new Row(spec.get());
        rows.add(row);
        startRow(row); // 添加即尝试启动
        updateButtonStates();
    }

    private void startSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.status != Status.RUNNING && row.status != Status.STARTING) {
            startRow(row);
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
        PortForward handle = row.handle;
        row.handle = null;
        if (handle != null) {
            stopInBackground(handle, row.spec.name());
        } else {
            // 正在启动中就被移除：让 startRow 完成后立即关闭
            row.stopPending = true;
        }
        rows.remove(row);
    }

    private void startRow(Row row) {
        row.status = Status.STARTING;
        row.detail = "";
        row.stopPending = false;
        table.refresh();
        updateButtonStates();
        executor.submit(() -> {
            try {
                PortForward handle = ssh.openForward(row.spec);
                Platform.runLater(() -> {
                    if (row.stopPending) {
                        // 启动过程中用户已要求停止/移除
                        row.status = Status.STOPPED;
                        row.detail = "";
                        stopInBackground(handle, row.spec.name());
                    } else {
                        row.handle = handle;
                        row.status = Status.RUNNING;
                        row.detail = String.valueOf(handle.boundPort());
                    }
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

    /** 在后台线程关闭转发，避免阻塞 FX 线程。 */
    private void stopInBackground(PortForward handle, String name) {
        Thread closer = new Thread(() -> {
            try {
                handle.close();
            } catch (RuntimeException ex) {
                log.warn("Error stopping forward {}: {}", name, ex.getMessage());
            }
        }, "forward-stop-" + name);
        closer.setDaemon(true);
        closer.start();
    }

    private void stopRow(Row row) {
        PortForward handle = row.handle;
        row.handle = null;
        row.status = Status.STOPPED;
        row.detail = "";
        table.refresh();
        updateButtonStates();
        if (handle != null) {
            stopInBackground(handle, row.spec.name());
        } else {
            // 正在启动中就被停止：让 startRow 完成后立即关闭
            row.stopPending = true;
        }
    }

    private String bindText(Row row) {
        PortForwardSpec spec = row.spec;
        int port = row.handle != null ? row.handle.boundPort() : spec.bindPort();
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
