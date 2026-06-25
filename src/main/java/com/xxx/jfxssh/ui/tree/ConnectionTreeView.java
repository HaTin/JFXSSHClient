package com.xxx.jfxssh.ui.tree;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ConnectionService;
import com.xxx.jfxssh.service.GroupNode;
import com.xxx.jfxssh.service.GroupService;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.ssh.SshService;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import com.xxx.jfxssh.ui.dialog.ConnectionDialog;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 连接树组件（见 docs/UI_DESIGN.md）。
 *
 * <p>从 {@link GroupService} / {@link ConnectionService} 加载真实数据，按分组
 * 嵌套展示连接，并通过右键菜单完成分组与连接的增删改、以及连接测试
 * （{@link SshService}）。所有数据变更后重新加载树。</p>
 */
public final class ConnectionTreeView {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTreeView.class);

    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final SshService sshService;
    private final TreeView<TreeNodeData> tree = new TreeView<>();

    /**
     * @param connectionService 连接服务
     * @param groupService      分组服务
     * @param sshService        SSH 服务
     */
    public ConnectionTreeView(ConnectionService connectionService,
                              GroupService groupService,
                              SshService sshService) {
        this.connectionService = connectionService;
        this.groupService = groupService;
        this.sshService = sshService;

        tree.setId("ConnectionTree");
        tree.setMinWidth(150);
        tree.setPrefWidth(Constants.CONNECTION_TREE_WIDTH);
        tree.setCellFactory(tv -> new ContextTreeCell());

        reload();
        I18n.currentLocaleProperty().addListener((obs, o, n) -> reload());
    }

    /**
     * @return 树视图节点
     */
    public TreeView<TreeNodeData> getView() {
        return tree;
    }

    /**
     * 在根（未分组）下新建连接，供菜单栏 File → New Connection 调用。
     */
    public void newConnection() {
        createConnection(null);
    }

    /**
     * 重新加载整棵树。
     */
    public void reload() {
        TreeItem<TreeNodeData> root = new TreeItem<>(TreeNodeData.root(I18n.t("tree.connections")));
        root.setExpanded(true);
        for (GroupNode node : groupService.findTree()) {
            root.getChildren().add(buildGroup(node));
        }
        for (Connection c : connectionService.findByGroup(null)) {
            root.getChildren().add(new TreeItem<>(TreeNodeData.connection(c)));
        }
        tree.setRoot(root);
    }

    private TreeItem<TreeNodeData> buildGroup(GroupNode node) {
        TreeItem<TreeNodeData> item = new TreeItem<>(TreeNodeData.group(node.getGroup()));
        item.setExpanded(true);
        for (GroupNode child : node.getChildren()) {
            item.getChildren().add(buildGroup(child));
        }
        for (Connection c : connectionService.findByGroup(node.getGroup().getId())) {
            item.getChildren().add(new TreeItem<>(TreeNodeData.connection(c)));
        }
        return item;
    }

    // ---- 分组操作 ----

    private void addGroup(Group parent) {
        Optional<String> name = UiDialogs.promptText("dialog.group.add_title", "dialog.group.add_prompt", null);
        name.ifPresent(n -> {
            Group g = new Group();
            g.setName(n);
            g.setParentId(parent == null ? null : parent.getId());
            groupService.save(g);
            reload();
        });
    }

    private void renameGroup(Group group) {
        Optional<String> name = UiDialogs.promptText(
                "dialog.group.rename_title", "dialog.group.rename_prompt", group.getName());
        name.ifPresent(n -> {
            groupService.rename(group.getId(), n);
            reload();
        });
    }

    private void deleteGroup(Group group) {
        if (UiDialogs.confirm("dialog.group.delete_title",
                I18n.t("dialog.group.delete_confirm", group.getName()))) {
            groupService.delete(group.getId());
            reload();
        }
    }

    // ---- 连接操作 ----

    private void createConnection(Group group) {
        new ConnectionDialog(null, groupService.findAll(), group == null ? null : group.getId())
                .showAndWait()
                .ifPresent(c -> {
                    connectionService.save(c);
                    reload();
                });
    }

    private void editConnection(Connection connection) {
        new ConnectionDialog(connection, groupService.findAll(), connection.getGroupId())
                .showAndWait()
                .ifPresent(c -> {
                    connectionService.update(c);
                    reload();
                });
    }

    private void duplicateConnection(Connection c) {
        Connection copy = new Connection();
        copy.setName((c.getName() == null ? c.getHost() : c.getName())
                + I18n.t("dialog.connection.duplicate_suffix"));
        copy.setHost(c.getHost());
        copy.setPort(c.getPort());
        copy.setUsername(c.getUsername());
        copy.setAuthType(c.getAuthType());
        copy.setPrivateKeyPath(c.getPrivateKeyPath());
        copy.setGroupId(c.getGroupId());
        copy.setRemark(c.getRemark());
        connectionService.save(copy);
        reload();
    }

    private void deleteConnection(Connection c) {
        if (UiDialogs.confirm("dialog.connection.delete_title",
                I18n.t("dialog.connection.delete_confirm", c.getName()))) {
            connectionService.delete(c.getId());
            reload();
        }
    }

    /**
     * 测试连接（无终端前的连接验证）。密码认证时弹窗输入密码。
     */
    private void connect(Connection c) {
        SshConnectionConfig.Builder builder = SshConnectionConfig.builder(c.getHost(), c.getUsername())
                .port(c.getPort())
                .authType(c.getAuthType());

        if (c.getAuthType() == AuthType.PRIVATE_KEY) {
            builder.privateKeyPath(c.getPrivateKeyPath());
        } else {
            Optional<String> pw = UiDialogs.promptPassword(
                    I18n.t("dialog.password.title"),
                    I18n.t("dialog.password.prompt", c.getHost()));
            if (pw.isEmpty()) {
                return;
            }
            builder.password(pw.get());
        }

        SshConnectionConfig config = builder.build();
        String target = c.getHost() + ":" + c.getPort();
        Thread worker = new Thread(() -> {
            boolean ok = sshService.test(config);
            Platform.runLater(() -> {
                if (ok) {
                    UiDialogs.info("app.title", I18n.t("msg.connect.success", target));
                } else {
                    UiDialogs.error(I18n.t("msg.connect.fail", target));
                }
            });
        }, "ssh-test");
        worker.setDaemon(true);
        worker.start();
        log.info("Testing connection to {}", target);
    }

    // ---- 右键菜单单元格 ----

    private MenuItem menuItem(String key, Runnable action) {
        MenuItem mi = new MenuItem();
        mi.textProperty().bind(I18n.tp(key));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    private final class ContextTreeCell extends TreeCell<TreeNodeData> {

        @Override
        protected void updateItem(TreeNodeData item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            setText(item.displayName());
            setContextMenu(buildMenu(item));
        }

        private ContextMenu buildMenu(TreeNodeData data) {
            switch (data.type()) {
                case ROOT:
                    return new ContextMenu(
                            menuItem("tree.menu.new_connection", () -> createConnection(null)),
                            menuItem("tree.menu.add_group", () -> addGroup(null)));
                case GROUP:
                    return new ContextMenu(
                            menuItem("tree.menu.new_connection", () -> createConnection(data.group())),
                            menuItem("tree.menu.add_group", () -> addGroup(data.group())),
                            menuItem("tree.menu.rename_group", () -> renameGroup(data.group())),
                            menuItem("tree.menu.delete_group", () -> deleteGroup(data.group())));
                case CONNECTION:
                default:
                    return new ContextMenu(
                            menuItem("tree.menu.connect", () -> connect(data.connection())),
                            new SeparatorMenuItem(),
                            menuItem("tree.menu.edit", () -> editConnection(data.connection())),
                            menuItem("tree.menu.duplicate", () -> duplicateConnection(data.connection())),
                            menuItem("tree.menu.delete", () -> deleteConnection(data.connection())));
            }
        }
    }
}
