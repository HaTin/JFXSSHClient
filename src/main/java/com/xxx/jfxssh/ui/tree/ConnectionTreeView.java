package com.xxx.jfxssh.ui.tree;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.config.AppConfig;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.service.ConnectionService;
import com.xxx.jfxssh.service.CredentialVault;
import com.xxx.jfxssh.service.GroupNode;
import com.xxx.jfxssh.service.GroupService;
import com.xxx.jfxssh.ssh.SshConnectionConfig;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import com.xxx.jfxssh.ui.dialog.ConnectionDialog;
import com.xxx.jfxssh.ui.dialog.UiDialogs;
import com.xxx.jfxssh.ui.terminal.TerminalLauncher;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

/**
 * 连接树组件（见 docs/UI_DESIGN.md）。
 *
 * <p>从 {@link GroupService} / {@link ConnectionService} 加载真实数据，按分组
 * 嵌套展示连接，并通过右键菜单完成分组与连接的增删改、以及打开终端
 * （委托 {@link TerminalLauncher}）。连接密码经 {@link CredentialVault} 加密保存、
 * 连接时解密；SSH 保活 / 超时取自设置。所有数据变更后重新加载树。</p>
 */
public final class ConnectionTreeView {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTreeView.class);

    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final TerminalLauncher terminalLauncher;
    private final CredentialVault vault;
    private final AppConfig config;
    private final TreeView<TreeNodeData> tree = new TreeView<>();

    /**
     * @param connectionService 连接服务
     * @param groupService      分组服务
     * @param terminalLauncher  打开终端的回调
     * @param vault             凭据保险库（密码加解密）
     * @param config            应用配置（SSH 保活 / 超时）
     */
    public ConnectionTreeView(ConnectionService connectionService,
                              GroupService groupService,
                              TerminalLauncher terminalLauncher,
                              CredentialVault vault,
                              AppConfig config) {
        this.connectionService = connectionService;
        this.groupService = groupService;
        this.terminalLauncher = terminalLauncher;
        this.vault = vault;
        this.config = config;

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
        ConnectionDialog dialog = new ConnectionDialog(
                null, groupService.findAll(), group == null ? null : group.getId());
        dialog.showAndWait().ifPresent(c -> {
            applyPassword(c, dialog.getPlainPassword());
            connectionService.save(c);
            reload();
        });
    }

    private void editConnection(Connection connection) {
        ConnectionDialog dialog = new ConnectionDialog(
                connection, groupService.findAll(), connection.getGroupId());
        dialog.showAndWait().ifPresent(c -> {
            applyPassword(c, dialog.getPlainPassword());
            connectionService.update(c);
            reload();
        });
    }

    /** 若输入了密码（密码认证），用保险库加密后写入；用户取消主密码则不保存密码。 */
    private void applyPassword(Connection c, String plain) {
        if (c.getAuthType() != AuthType.PASSWORD || plain == null || plain.isBlank()) {
            return;
        }
        if (ensureUnlocked()) {
            c.setPasswordEnc(vault.encrypt(plain));
        } else {
            UiDialogs.info("app.title", I18n.t("dialog.master.not_saved"));
        }
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
        copy.setPasswordEnc(c.getPasswordEnc());
        copy.setGroupId(c.getGroupId());
        copy.setRemark(c.getRemark());
        copy.setTerminalType(c.getTerminalType());
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
     * 打开终端：解析认证（已存密码→主密码解锁解密，可重试或改用其他方式），
     * 委托打开终端标签页。
     */
    private void connect(Connection c) {
        SshConnectionConfig config = buildConfig(c);
        if (config == null) {
            return;
        }
        log.info("Opening terminal for {}:{}", c.getHost(), c.getPort());
        terminalLauncher.open(c, config);
    }

    /** 构建连接配置；用户全程取消返回 null。 */
    private SshConnectionConfig buildConfig(Connection c) {
        int keepAlive = config.getInt(AppConfig.KEY_SSH_KEEPALIVE, AppConfig.DEFAULT_SSH_KEEPALIVE);
        int timeout = config.getInt(AppConfig.KEY_SSH_TIMEOUT, AppConfig.DEFAULT_SSH_TIMEOUT);
        SshConnectionConfig.Builder builder = SshConnectionConfig.builder(c.getHost(), c.getUsername())
                .port(c.getPort())
                .keepAliveInterval(Duration.ofSeconds(keepAlive))
                .connectTimeout(Duration.ofSeconds(timeout))
                .authTimeout(Duration.ofSeconds(timeout));
        if (c.getTerminalType() != null && !c.getTerminalType().isBlank()) {
            builder.ptyType(c.getTerminalType());
        }

        if (c.getAuthType() == AuthType.PRIVATE_KEY) {
            return builder.authType(AuthType.PRIVATE_KEY)
                    .privateKeyPath(c.getPrivateKeyPath())
                    .build();
        }

        String enc = c.getPasswordEnc();
        if (enc != null && !enc.isBlank()) {
            if (vault.isUnlocked()) {
                String pw = tryDecrypt(enc);
                if (pw != null) {
                    return builder.authType(AuthType.PASSWORD).password(pw).build();
                }
            } else {
                boolean showError = false;
                while (true) {
                    UiDialogs.UnlockRequest req = UiDialogs.promptMasterUnlock(showError);
                    if (req.action() == UiDialogs.UnlockAction.CANCEL) {
                        return null;
                    }
                    if (req.action() == UiDialogs.UnlockAction.OTHER) {
                        break;
                    }
                    boolean ok;
                    try {
                        ok = vault.unlock(req.password());
                    } finally {
                        CredentialVault.wipe(req.password());
                    }
                    if (ok) {
                        String pw = tryDecrypt(enc);
                        if (pw != null) {
                            return builder.authType(AuthType.PASSWORD).password(pw).build();
                        }
                        break;
                    }
                    showError = true;
                }
            }
        }
        return chooseMethod(c, builder);
    }

    /** 让用户选择认证方式（密码 / 私钥）并补全配置；取消返回 null。 */
    private SshConnectionConfig chooseMethod(Connection c, SshConnectionConfig.Builder builder) {
        UiDialogs.AuthChoice choice = UiDialogs.chooseAuthMethod();
        if (choice == UiDialogs.AuthChoice.CANCEL) {
            return null;
        }
        if (choice == UiDialogs.AuthChoice.PASSWORD) {
            Optional<String> pw = UiDialogs.promptPassword(
                    I18n.t("dialog.password.title"),
                    I18n.t("dialog.password.prompt", c.getHost()));
            if (pw.isEmpty()) {
                return null;
            }
            return builder.authType(AuthType.PASSWORD).password(pw.get()).build();
        }
        // PRIVATE_KEY
        Optional<File> keyFile = UiDialogs.chooseKeyFile();
        if (keyFile.isEmpty()) {
            return null;
        }
        builder.authType(AuthType.PRIVATE_KEY).privateKeyPath(keyFile.get().getAbsolutePath());
        UiDialogs.promptPassword(I18n.t("dialog.password.title"), I18n.t("dialog.key.passphrase_prompt"))
                .filter(p -> !p.isBlank())
                .ifPresent(builder::passphrase);
        return builder.build();
    }

    private String tryDecrypt(String enc) {
        try {
            return vault.decrypt(enc);
        } catch (RuntimeException e) {
            log.warn("Failed to decrypt stored password: {}", e.getMessage());
            return null;
        }
    }

    /** 确保保险库已解锁：未初始化则引导设置主密码，已初始化则要求输入主密码。 */
    private boolean ensureUnlocked() {
        if (vault.isUnlocked()) {
            return true;
        }
        if (!vault.isInitialized()) {
            Optional<char[]> master = UiDialogs.promptNewMasterPassword();
            if (master.isEmpty()) {
                return false;
            }
            try {
                vault.initialize(master.get());
            } finally {
                CredentialVault.wipe(master.get());
            }
            return true;
        }
        Optional<char[]> master = UiDialogs.promptMasterPassword();
        if (master.isEmpty()) {
            return false;
        }
        boolean ok;
        try {
            ok = vault.unlock(master.get());
        } finally {
            CredentialVault.wipe(master.get());
        }
        if (!ok) {
            UiDialogs.error(I18n.t("dialog.master.wrong"));
        }
        return ok;
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
