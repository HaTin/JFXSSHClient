package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.SftpEntry;
import com.xxx.jfxssh.ssh.SftpSession;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 双栏 SFTP 浏览器的<b>远程</b>面板：浏览远程目录、导航、新建目录 / 重命名 / 删除。
 *
 * <p>所有 SFTP 调用提交到窗口的单线程执行器（{@link SftpSession} 非线程安全），结果经
 * {@link Platform#runLater} 回 FX 线程。作为下载来源与上传目标。</p>
 */
final class RemotePane {

    private static final Logger log = LoggerFactory.getLogger(RemotePane.class);

    private static final Comparator<SftpEntry> ORDER =
            Comparator.comparing(SftpEntry::directory).reversed()
                    .thenComparing(e -> e.name().toLowerCase());

    private final VBox view = new VBox();
    private final TextField pathField = new TextField();
    private final TableView<SftpEntry> table = new TableView<>();
    private final ObservableList<SftpEntry> rows = FXCollections.observableArrayList();

    private final SftpSession sftp;
    private final ExecutorService executor;
    private final Consumer<String> status;
    private final Runnable onDownload;

    private volatile String currentPath = "/";

    RemotePane(SftpSession sftp, ExecutorService executor, Consumer<String> status, Runnable onDownload) {
        this.sftp = sftp;
        this.executor = executor;
        this.status = status;
        this.onDownload = onDownload;
        buildView();
        navigate(".");
    }

    VBox getView() {
        return view;
    }

    String currentPath() {
        return currentPath;
    }

    /** @return 选中条目，无选中返回 null */
    SftpEntry selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** 拼接当前目录下某名称的远程路径。 */
    String resolve(String name) {
        return join(currentPath, name);
    }

    /** 在执行器上重新加载当前目录。 */
    void refresh() {
        navigate(currentPath);
    }

    private void buildView() {
        Label title = new Label(I18n.t("sftp.pane.remote"));
        title.setStyle("-fx-font-weight: bold;");

        pathField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                navigate(pathField.getText().replace('\\', '/'));
            }
        });
        HBox.setHgrow(pathField, Priority.ALWAYS);
        Button up = new Button(I18n.t("sftp.button.up"));
        up.setOnAction(e -> navigate(parentOf(currentPath)));
        Button refresh = new Button(I18n.t("sftp.button.refresh"));
        refresh.setOnAction(e -> refresh());
        HBox bar = new HBox(6, pathField, up, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);

        buildTable();

        VBox.setVgrow(table, Priority.ALWAYS);
        view.setSpacing(6);
        view.setPadding(new Insets(6));
        view.getChildren().addAll(title, bar, table);
    }

    private void buildTable() {
        TableColumn<SftpEntry, String> nameCol = new TableColumn<>(I18n.t("sftp.column.name"));
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));
        nameCol.setComparator(String.CASE_INSENSITIVE_ORDER);
        nameCol.setPrefWidth(220);

        TableColumn<SftpEntry, Long> sizeCol = new TableColumn<>(I18n.t("sftp.column.size"));
        sizeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().size()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                SftpEntry e = empty || getTableRow() == null ? null : getTableRow().getItem();
                setText(empty || e == null ? null : (e.directory() ? "-" : SftpFormat.humanSize(value)));
            }
        });
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        sizeCol.setPrefWidth(90);

        TableColumn<SftpEntry, String> typeCol = new TableColumn<>(I18n.t("sftp.column.type"));
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(typeLabel(cd.getValue())));
        typeCol.setPrefWidth(80);

        TableColumn<SftpEntry, Long> modCol = new TableColumn<>(I18n.t("sftp.column.modified"));
        modCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().modifiedEpochMillis()));
        modCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : SftpFormat.time(value));
            }
        });
        modCol.setPrefWidth(150);

        table.getColumns().add(nameCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(modCol);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setContextMenu(buildMenu());

        table.setRowFactory(tv -> {
            TableRow<SftpEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && row.getItem().directory()) {
                    navigate(join(currentPath, row.getItem().name()));
                }
            });
            return row;
        });
    }

    private ContextMenu buildMenu() {
        MenuItem download = new MenuItem(I18n.t("sftp.menu.download"));
        download.setOnAction(e -> onDownload.run());
        MenuItem mkdir = new MenuItem(I18n.t("sftp.menu.mkdir"));
        mkdir.setOnAction(e -> mkdir());
        MenuItem rename = new MenuItem(I18n.t("sftp.menu.rename"));
        rename.setOnAction(e -> rename());
        MenuItem delete = new MenuItem(I18n.t("sftp.menu.delete"));
        delete.setOnAction(e -> delete());
        MenuItem refresh = new MenuItem(I18n.t("sftp.button.refresh"));
        refresh.setOnAction(e -> refresh());
        return new ContextMenu(download, new SeparatorMenuItem(), mkdir, rename, delete, refresh);
    }

    private void navigate(String path) {
        status.accept(I18n.t("sftp.status.loading"));
        executor.submit(() -> {
            try {
                String canonical = sftp.canonicalPath(path);
                List<SftpEntry> entries = sftp.list(canonical);
                entries.sort(ORDER);
                Platform.runLater(() -> {
                    currentPath = canonical;
                    pathField.setText(canonical);
                    rows.setAll(entries);
                    table.getSortOrder().clear();
                    status.accept(I18n.t("sftp.status.items", String.valueOf(entries.size())));
                });
            } catch (RuntimeException ex) {
                log.warn("Remote list failed for {}: {}", path, ex.getMessage());
                Platform.runLater(() -> status.accept(I18n.t("sftp.status.load_fail", path)));
            }
        });
    }

    private void mkdir() {
        Optional<String> name = UiDialogs.promptText("sftp.menu.mkdir", "sftp.prompt.mkdir", "");
        if (name.isEmpty()) {
            return;
        }
        String path = join(currentPath, name.get());
        runMutation(() -> sftp.mkdir(path), I18n.t("sftp.error.mkdir", name.get()));
    }

    private void rename() {
        SftpEntry entry = selected();
        if (entry == null) {
            return;
        }
        Optional<String> name = UiDialogs.promptText("sftp.menu.rename", "sftp.prompt.rename", entry.name());
        if (name.isEmpty() || name.get().equals(entry.name())) {
            return;
        }
        String from = join(currentPath, entry.name());
        String to = join(currentPath, name.get());
        runMutation(() -> sftp.rename(from, to), I18n.t("sftp.error.rename", entry.name()));
    }

    private void delete() {
        SftpEntry entry = selected();
        if (entry == null) {
            return;
        }
        if (!UiDialogs.confirm("sftp.menu.delete", I18n.t("sftp.confirm.delete", entry.name()))) {
            return;
        }
        String path = join(currentPath, entry.name());
        runMutation(() -> sftp.delete(path, entry.directory()), I18n.t("sftp.error.delete", entry.name()));
    }

    /** 在执行器上执行一个改动操作，成功后刷新，失败弹错并刷新。 */
    private void runMutation(Runnable op, String errorMessage) {
        executor.submit(() -> {
            try {
                op.run();
                Platform.runLater(this::refresh);
            } catch (RuntimeException ex) {
                log.warn("{}: {}", errorMessage, ex.getMessage());
                Platform.runLater(() -> {
                    UiDialogs.error(errorMessage);
                    refresh();
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

    private static String join(String base, String name) {
        return base.endsWith("/") ? base + name : base + "/" + name;
    }

    private static String parentOf(String path) {
        if (path == null || path.equals("/") || path.isBlank()) {
            return "/";
        }
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = p.lastIndexOf('/');
        return idx <= 0 ? "/" : p.substring(0, idx);
    }
}
