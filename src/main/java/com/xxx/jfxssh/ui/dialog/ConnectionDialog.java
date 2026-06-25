package com.xxx.jfxssh.ui.dialog;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

/**
 * 新建 / 编辑连接对话框（GridPane，见 docs/UI_DESIGN.md）。
 *
 * <p>采集连接字段并返回 {@link Connection}。密码字段按 UI 规范展示，但当前
 * 不持久化（加密待任务7，见 password_hint 提示）。</p>
 */
public final class ConnectionDialog {

    private final Dialog<Connection> dialog = new Dialog<>();
    private final Connection target;

    private final TextField nameField = new TextField();
    private final TextField hostField = new TextField();
    private final TextField portField = new TextField();
    private final TextField usernameField = new TextField();
    private final ComboBox<AuthType> authCombo = new ComboBox<>();
    private final PasswordField passwordField = new PasswordField();
    private final TextField keyPathField = new TextField();
    private final ComboBox<Group> groupCombo = new ComboBox<>();
    private final ComboBox<String> terminalTypeCombo = new ComboBox<>();
    private final TextField remarkField = new TextField();

    /**
     * @param existing         编辑时传入已有连接，新建时为 null
     * @param groups           可选分组列表
     * @param preselectGroupId 预选分组 id（可空）
     */
    public ConnectionDialog(Connection existing, List<Group> groups, Long preselectGroupId) {
        this.target = existing != null ? existing : new Connection();

        dialog.setTitle(I18n.t(existing != null
                ? "dialog.connection.edit_title" : "dialog.connection.new_title"));

        ButtonType okType = new ButtonType(I18n.t("button.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.t("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, cancelType);
        dialog.getDialogPane().setContent(buildForm(groups));

        fill(existing, preselectGroupId);
        updateAuthVisibility();
        authCombo.valueProperty().addListener((o, a, b) -> updateAuthVisibility());

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            if (isBlank(nameField.getText()) || isBlank(hostField.getText())) {
                UiDialogs.error(I18n.t("dialog.connection.required"));
                e.consume();
            }
        });

        dialog.setResultConverter(bt -> bt == okType ? collect() : null);
    }

    /**
     * @return 用户确认返回连接，取消返回 empty
     */
    public Optional<Connection> showAndWait() {
        return dialog.showAndWait();
    }

    /**
     * @return 用户在密码框输入的明文（密码认证用，可空）；加密与持久化由调用方处理
     */
    public String getPlainPassword() {
        return passwordField.getText();
    }

    private GridPane buildForm(List<Group> groups) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        portField.setPromptText(String.valueOf(Constants.DEFAULT_PORT));

        authCombo.getItems().setAll(AuthType.PASSWORD, AuthType.PRIVATE_KEY);
        authCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(AuthType t) {
                if (t == AuthType.PRIVATE_KEY) {
                    return I18n.t("auth.private_key");
                }
                return I18n.t("auth.password");
            }

            @Override
            public AuthType fromString(String s) {
                return AuthType.PASSWORD;
            }
        });

        groupCombo.getItems().add(null);
        if (groups != null) {
            groupCombo.getItems().addAll(groups);
        }
        groupCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group g) {
                return g == null ? I18n.t("dialog.connection.group_none") : g.getName();
            }

            @Override
            public Group fromString(String s) {
                return null;
            }
        });

        terminalTypeCombo.getItems().setAll(
                "xterm-256color", "xterm", "vt100", "vt220", "ansi", "linux", "screen");

        Button browse = new Button(I18n.t("dialog.connection.browse"));
        browse.setOnAction(e -> chooseKey());
        HBox keyBox = new HBox(8, keyPathField, browse);
        HBox.setHgrow(keyPathField, javafx.scene.layout.Priority.ALWAYS);

        Label passwordHint = new Label(I18n.t("dialog.connection.password_hint"));
        passwordHint.setWrapText(true);

        int r = 0;
        grid.addRow(r++, new Label(I18n.t("dialog.connection.name")), nameField);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.host")), hostField);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.port")), portField);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.username")), usernameField);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.auth")), authCombo);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.password")), passwordField);
        grid.add(passwordHint, 1, r++);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.private_key_path")), keyBox);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.group")), groupCombo);
        grid.addRow(r++, new Label(I18n.t("dialog.connection.terminal_type")), terminalTypeCombo);
        grid.addRow(r, new Label(I18n.t("dialog.connection.remark")), remarkField);

        // 记录密码相关行用于显隐
        passwordRow = new javafx.scene.Node[]{passwordField, passwordHint};
        keyRow = new javafx.scene.Node[]{keyBox};
        return grid;
    }

    private javafx.scene.Node[] passwordRow;
    private javafx.scene.Node[] keyRow;

    private void updateAuthVisibility() {
        boolean key = authCombo.getValue() == AuthType.PRIVATE_KEY;
        toggle(keyRow, key);
        toggle(passwordRow, !key);
    }

    private void toggle(javafx.scene.Node[] nodes, boolean visible) {
        if (nodes == null) {
            return;
        }
        for (javafx.scene.Node n : nodes) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    private void fill(Connection c, Long preselectGroupId) {
        if (c == null) {
            portField.setText(String.valueOf(Constants.DEFAULT_PORT));
            authCombo.setValue(AuthType.PASSWORD);
            terminalTypeCombo.setValue("xterm-256color");
            selectGroup(preselectGroupId);
            return;
        }
        nameField.setText(nullToEmpty(c.getName()));
        hostField.setText(nullToEmpty(c.getHost()));
        portField.setText(String.valueOf(c.getPort() == 0 ? Constants.DEFAULT_PORT : c.getPort()));
        usernameField.setText(nullToEmpty(c.getUsername()));
        authCombo.setValue(c.getAuthType() == null ? AuthType.PASSWORD : c.getAuthType());
        keyPathField.setText(nullToEmpty(c.getPrivateKeyPath()));
        remarkField.setText(nullToEmpty(c.getRemark()));
        terminalTypeCombo.setValue(c.getTerminalType() == null || c.getTerminalType().isBlank()
                ? "xterm-256color" : c.getTerminalType());
        selectGroup(preselectGroupId != null ? preselectGroupId : c.getGroupId());
    }

    private void selectGroup(Long groupId) {
        if (groupId == null) {
            groupCombo.setValue(null);
            return;
        }
        for (Group g : groupCombo.getItems()) {
            if (g != null && g.getId() != null && g.getId().equals(groupId)) {
                groupCombo.setValue(g);
                return;
            }
        }
        groupCombo.setValue(null);
    }

    private void chooseKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("dialog.connection.private_key_path"));
        java.io.File file = chooser.showOpenDialog(dialog.getOwner());
        if (file != null) {
            keyPathField.setText(file.getAbsolutePath());
        }
    }

    private Connection collect() {
        target.setName(nameField.getText().trim());
        target.setHost(hostField.getText().trim());
        target.setPort(parsePort(portField.getText()));
        target.setUsername(emptyToNull(usernameField.getText()));
        AuthType auth = authCombo.getValue() == null ? AuthType.PASSWORD : authCombo.getValue();
        target.setAuthType(auth);
        target.setPrivateKeyPath(auth == AuthType.PRIVATE_KEY ? emptyToNull(keyPathField.getText()) : null);
        Group group = groupCombo.getValue();
        target.setGroupId(group == null ? null : group.getId());
        target.setRemark(emptyToNull(remarkField.getText()));
        target.setTerminalType(terminalTypeCombo.getValue());
        // 注：不写 passwordEnc（明文不落库，加密见任务7）
        return target;
    }

    private int parsePort(String s) {
        try {
            int p = Integer.parseInt(s.trim());
            return (p < 1 || p > 65535) ? Constants.DEFAULT_PORT : p;
        } catch (NumberFormatException e) {
            return Constants.DEFAULT_PORT;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
