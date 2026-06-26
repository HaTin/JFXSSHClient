package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 双栏 SFTP 浏览器的<b>本地</b>面板：浏览本机文件系统、导航、新建目录 / 重命名 / 删除。
 *
 * <p>本地文件操作经 {@code java.nio.file} 在 FX 线程完成（本地 I/O 快）。作为上传来源与
 * 下载目标，向窗口暴露当前目录与选中项。</p>
 */
final class LocalPane {

    private static final Logger log = LoggerFactory.getLogger(LocalPane.class);

    /** 本地条目快照（避免渲染时反复读属性）。 */
    record LocalEntry(Path path, String name, boolean directory, long size, long modified) {
    }

    private static final Comparator<LocalEntry> ORDER =
            Comparator.comparing(LocalEntry::directory).reversed()
                    .thenComparing(e -> e.name().toLowerCase());

    private final VBox view = new VBox();
    private final TextField pathField = new TextField();
    private final TableView<LocalEntry> table = new TableView<>();
    private final ObservableList<LocalEntry> rows = FXCollections.observableArrayList();
    private final Consumer<String> status;
    private final Runnable onUpload;

    private Path currentDir;

    LocalPane(Consumer<String> status, Runnable onUpload) {
        this.status = status;
        this.onUpload = onUpload;
        this.currentDir = Paths.get(System.getProperty("user.home"));
        buildView();
        navigate(currentDir);
    }

    VBox getView() {
        return view;
    }

    Path currentDir() {
        return currentDir;
    }

    /** @return 选中条目，无选中返回 null */
    LocalEntry selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** 刷新当前目录（如上传后远程无关，下载后本地需要）。 */
    void refresh() {
        navigate(currentDir);
    }

    private void buildView() {
        Label title = new Label(I18n.t("sftp.pane.local"));
        title.setStyle("-fx-font-weight: bold;");

        pathField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                navigate(Paths.get(pathField.getText()));
            }
        });
        HBox.setHgrow(pathField, Priority.ALWAYS);
        Button up = new Button(I18n.t("sftp.button.up"));
        up.setOnAction(e -> {
            Path parent = currentDir.getParent();
            if (parent != null) {
                navigate(parent);
            }
        });
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
        TableColumn<LocalEntry, String> nameCol = new TableColumn<>(I18n.t("sftp.column.name"));
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));
        nameCol.setComparator(String.CASE_INSENSITIVE_ORDER);
        nameCol.setPrefWidth(220);

        TableColumn<LocalEntry, Long> sizeCol = new TableColumn<>(I18n.t("sftp.column.size"));
        sizeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().size()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                LocalEntry e = empty || getTableRow() == null ? null : getTableRow().getItem();
                setText(empty || e == null ? null : (e.directory() ? "-" : SftpFormat.humanSize(value)));
            }
        });
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        sizeCol.setPrefWidth(90);

        TableColumn<LocalEntry, Long> modCol = new TableColumn<>(I18n.t("sftp.column.modified"));
        modCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().modified()));
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
        table.getColumns().add(modCol);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setContextMenu(buildMenu());

        table.setRowFactory(tv -> {
            TableRow<LocalEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && row.getItem().directory()) {
                    navigate(row.getItem().path());
                }
            });
            return row;
        });
    }

    private ContextMenu buildMenu() {
        MenuItem upload = new MenuItem(I18n.t("sftp.menu.upload"));
        upload.setOnAction(e -> onUpload.run());
        MenuItem mkdir = new MenuItem(I18n.t("sftp.menu.mkdir"));
        mkdir.setOnAction(e -> mkdir());
        MenuItem rename = new MenuItem(I18n.t("sftp.menu.rename"));
        rename.setOnAction(e -> rename());
        MenuItem delete = new MenuItem(I18n.t("sftp.menu.delete"));
        delete.setOnAction(e -> delete());
        MenuItem refresh = new MenuItem(I18n.t("sftp.button.refresh"));
        refresh.setOnAction(e -> refresh());
        return new ContextMenu(upload, new SeparatorMenuItem(), mkdir, rename, delete, refresh);
    }

    private void navigate(Path dir) {
        if (dir == null) {
            return;
        }
        Path target = dir.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.list(target)) {
            List<LocalEntry> entries = new ArrayList<>();
            stream.forEach(p -> entries.add(toEntry(p)));
            entries.sort(ORDER);
            currentDir = target;
            pathField.setText(target.toString());
            rows.setAll(entries);
            status.accept(I18n.t("sftp.status.items", String.valueOf(entries.size())));
        } catch (IOException | RuntimeException ex) {
            log.warn("Local list failed for {}: {}", target, ex.getMessage());
            status.accept(I18n.t("sftp.status.load_fail", target.toString()));
        }
    }

    private LocalEntry toEntry(Path p) {
        boolean dir = Files.isDirectory(p);
        long size = 0;
        long modified = 0;
        try {
            if (!dir) {
                size = Files.size(p);
            }
            modified = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ignore) {
            // 无法读取属性时以 0 显示
        }
        return new LocalEntry(p, p.getFileName().toString(), dir, size, modified);
    }

    private void mkdir() {
        Optional<String> name = UiDialogs.promptText("sftp.menu.mkdir", "sftp.prompt.mkdir", "");
        if (name.isEmpty()) {
            return;
        }
        try {
            Files.createDirectory(currentDir.resolve(name.get()));
            refresh();
        } catch (IOException ex) {
            UiDialogs.error(SftpErrors.message("sftp.error.mkdir", name.get(), ex));
        }
    }

    private void rename() {
        LocalEntry entry = selected();
        if (entry == null) {
            return;
        }
        Optional<String> name = UiDialogs.promptText("sftp.menu.rename", "sftp.prompt.rename", entry.name());
        if (name.isEmpty() || name.get().equals(entry.name())) {
            return;
        }
        try {
            Files.move(entry.path(), currentDir.resolve(name.get()));
            refresh();
        } catch (IOException ex) {
            UiDialogs.error(SftpErrors.message("sftp.error.rename", entry.name(), ex));
        }
    }

    private void delete() {
        LocalEntry entry = selected();
        if (entry == null) {
            return;
        }
        if (!UiDialogs.confirm("sftp.menu.delete", I18n.t("sftp.confirm.delete", entry.name()))) {
            return;
        }
        try {
            if (entry.directory()) {
                deleteRecursively(entry.path());
            } else {
                Files.delete(entry.path());
            }
            refresh();
        } catch (IOException ex) {
            UiDialogs.error(SftpErrors.message("sftp.error.delete", entry.name(), ex));
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.delete(p);
            }
        }
    }
}
