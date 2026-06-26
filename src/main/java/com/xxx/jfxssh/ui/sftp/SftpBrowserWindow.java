package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpEntry;
import com.xxx.jfxssh.ssh.SftpSession;
import com.xxx.jfxssh.ssh.SshSession;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 独立的 SFTP 文件浏览窗口（每个连接一个）。
 *
 * <p>顶部地址栏（路径 + 上级 + 刷新），中部 {@link TableView} 列出远程文件（名称 / 大小 /
 * 类型 / 修改时间，支持点列头排序，默认目录优先），底部状态栏。双击目录进入、双击文件下载。</p>
 *
 * <p>所有 SFTP 调用经<b>单线程执行器</b>串行化（{@link SftpSession} 非线程安全），结果经
 * {@link Platform#runLater} 回到 FX 线程。关闭窗口时释放执行器、SFTP 与底层 SSH 会话。</p>
 */
public final class SftpBrowserWindow {

    private static final Logger log = LoggerFactory.getLogger(SftpBrowserWindow.class);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** 目录优先，再按名称（不区分大小写）。 */
    private static final Comparator<SftpEntry> DEFAULT_ORDER =
            Comparator.comparing(SftpEntry::directory).reversed()
                    .thenComparing(e -> e.name().toLowerCase());

    private final Stage stage;
    private final SftpSession sftp;
    private final SshSession ssh;
    private final ExecutorService executor;
    private final Consumer<SftpBrowserWindow> onClose;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private final TextField pathField = new TextField();
    private final TableView<SftpEntry> table = new TableView<>();
    private final ObservableList<SftpEntry> rows = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();

    private volatile String currentPath = "/";

    /**
     * @param title   连接显示名（用于窗口标题）
     * @param sftp    已打开的 SFTP 会话
     * @param ssh     依附的 SSH 会话（窗口关闭时一并关闭）
     * @param owner   父窗口（可空）
     * @param onClose 关闭回调（从启动器注册表移除）
     */
    public SftpBrowserWindow(String title, SftpSession sftp, SshSession ssh,
                             Window owner, Consumer<SftpBrowserWindow> onClose) {
        this.sftp = sftp;
        this.ssh = ssh;
        this.onClose = onClose;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sftp-ops-" + title);
            t.setDaemon(true);
            return t;
        });

        this.stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.t("sftp.title", title));
        stage.setScene(new Scene(buildRoot(), 760, 520));
        stage.setOnCloseRequest(e -> close());

        navigate(".");
    }

    /** 显示窗口。 */
    public void show() {
        stage.show();
    }

    /** 关闭窗口并释放执行器、SFTP 与 SSH 会话（幂等）。 */
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        Thread closer = new Thread(() -> {
            try {
                sftp.close();
            } catch (RuntimeException ex) {
                log.warn("Error closing SFTP: {}", ex.getMessage());
            }
            ssh.close();
        }, "sftp-close");
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
        BorderPane root = new BorderPane();
        root.setTop(buildAddressBar());
        root.setCenter(buildTable());
        root.setBottom(buildStatusBar());
        return root;
    }

    private HBox buildAddressBar() {
        pathField.setEditable(true);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                navigate(pathField.getText().replace('\\', '/'));
            }
        });

        Button up = new Button(I18n.t("sftp.button.up"));
        up.setOnAction(e -> navigate(parentOf(currentPath)));
        Button refresh = new Button(I18n.t("sftp.button.refresh"));
        refresh.setOnAction(e -> navigate(currentPath));

        HBox bar = new HBox(6, pathField, up, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));
        return bar;
    }

    private TableView<SftpEntry> buildTable() {
        TableColumn<SftpEntry, String> nameCol = new TableColumn<>(I18n.t("sftp.column.name"));
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));
        nameCol.setComparator(String.CASE_INSENSITIVE_ORDER);
        nameCol.setPrefWidth(320);

        TableColumn<SftpEntry, Long> sizeCol = new TableColumn<>(I18n.t("sftp.column.size"));
        sizeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().size()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                SftpEntry entry = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (empty || value == null || entry == null) {
                    setText(null);
                } else {
                    setText(entry.directory() ? "-" : humanSize(value));
                }
            }
        });
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        sizeCol.setPrefWidth(110);

        TableColumn<SftpEntry, String> typeCol = new TableColumn<>(I18n.t("sftp.column.type"));
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(typeLabel(cd.getValue())));
        typeCol.setPrefWidth(90);

        TableColumn<SftpEntry, Long> modCol = new TableColumn<>(I18n.t("sftp.column.modified"));
        modCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().modifiedEpochMillis()));
        modCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null || value == 0L ? (empty ? null : "-")
                        : TIME_FMT.format(Instant.ofEpochMilli(value)));
            }
        });
        modCol.setPrefWidth(170);

        table.getColumns().add(nameCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(modCol);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setRowFactory(tv -> {
            TableRow<SftpEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    onActivate(row.getItem());
                }
            });
            return row;
        });
        return table;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    /** 双击：目录进入，文件下载。符号链接首版当作文件处理（不导航）。 */
    private void onActivate(SftpEntry entry) {
        if (entry.directory()) {
            navigate(join(currentPath, entry.name()));
        } else {
            download(entry);
        }
    }

    /** 在执行器上加载目录，成功后刷新表格与路径。 */
    private void navigate(String path) {
        statusLabel.setText(I18n.t("sftp.status.loading"));
        executor.submit(() -> {
            try {
                String canonical = sftp.canonicalPath(path);
                List<SftpEntry> entries = sftp.list(canonical);
                entries.sort(DEFAULT_ORDER);
                Platform.runLater(() -> {
                    currentPath = canonical;
                    pathField.setText(canonical);
                    rows.setAll(entries);
                    table.getSortOrder().clear();
                    statusLabel.setText(I18n.t("sftp.status.items", String.valueOf(entries.size())));
                });
            } catch (RuntimeException ex) {
                log.warn("List failed for {}: {}", path, ex.getMessage());
                Platform.runLater(() -> statusLabel.setText(I18n.t("sftp.status.load_fail", path)));
            }
        });
    }

    /** 选择本地保存位置后在执行器上下载。 */
    private void download(SftpEntry entry) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("dialog.sftp.download.title"));
        chooser.setInitialFileName(entry.name());
        File local = chooser.showSaveDialog(stage);
        if (local == null) {
            return;
        }
        String remote = join(currentPath, entry.name());
        statusLabel.setText(I18n.t("sftp.status.downloading", entry.name()));
        executor.submit(() -> {
            try {
                sftp.download(remote, local);
                Platform.runLater(() -> statusLabel.setText(
                        I18n.t("sftp.status.download_done", local.getName())));
            } catch (RuntimeException ex) {
                log.warn("Download failed for {}: {}", remote, ex.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText(I18n.t("sftp.status.items", String.valueOf(rows.size())));
                    UiDialogs.error(I18n.t("msg.sftp.download.fail", entry.name()));
                });
            }
        });
    }

    private String typeLabel(SftpEntry e) {
        if (e.directory()) {
            return I18n.t("sftp.type.directory");
        }
        if (e.symlink()) {
            return I18n.t("sftp.type.symlink");
        }
        return I18n.t("sftp.type.file");
    }

    /** 拼接远程路径（始终用 '/'）。 */
    private static String join(String base, String name) {
        if (base.endsWith("/")) {
            return base + name;
        }
        return base + "/" + name;
    }

    /** 远程父目录（POSIX）。 */
    private static String parentOf(String path) {
        if (path == null || path.equals("/") || path.isBlank()) {
            return "/";
        }
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = p.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return p.substring(0, idx);
    }

    /** 人类可读字节大小。 */
    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return String.format("%.1f %s", value, units[unit]);
    }
}
