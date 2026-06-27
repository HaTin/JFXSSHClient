package com.xxx.jfxssh.ui.forward;

import com.xxx.jfxssh.common.i18n.I18n;
import com.xxx.jfxssh.ssh.PortForwardSpec;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.Optional;

/**
 * 添加端口转发规则的表单对话框。
 *
 * <p>类型 = 动态(SOCKS) 时禁用目标主机 / 端口。OK 时做基础校验，返回 {@link PortForwardSpec}。</p>
 */
final class PortForwardDialog {

    private final Dialog<PortForwardSpec> dialog = new Dialog<>();
    private final ComboBox<PortForwardSpec.Type> typeBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final TextField bindHostField = new TextField("127.0.0.1");
    private final TextField bindPortField = new TextField();
    private final TextField destHostField = new TextField();
    private final TextField destPortField = new TextField();
    private final Label errorLabel = new Label();
    private final Label hintLabel = new Label();

    PortForwardDialog() {
        dialog.setTitle(I18n.t("forward.dialog.title"));
        ButtonType ok = new ButtonType(I18n.t("button.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.t("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        typeBox.getItems().addAll(PortForwardSpec.Type.values());
        typeBox.getSelectionModel().select(PortForwardSpec.Type.LOCAL);
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PortForwardSpec.Type t) {
                return t == null ? "" : I18n.t(typeKey(t));
            }

            @Override
            public PortForwardSpec.Type fromString(String s) {
                return null;
            }
        });
        typeBox.valueProperty().addListener((o, a, b) -> updateDestState());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label(I18n.t("forward.dialog.type")), typeBox);
        grid.addRow(1, new Label(I18n.t("forward.dialog.name")), nameField);
        grid.addRow(2, new Label(I18n.t("forward.dialog.bind_host")), bindHostField);
        grid.addRow(3, new Label(I18n.t("forward.dialog.bind_port")), bindPortField);
        grid.addRow(4, new Label(I18n.t("forward.dialog.dest_host")), destHostField);
        grid.addRow(5, new Label(I18n.t("forward.dialog.dest_port")), destPortField);
        errorLabel.setStyle("-fx-text-fill: #d33;");
        grid.add(errorLabel, 0, 6, 2, 1);
        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        grid.add(hintLabel, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(grid);

        updateDestState();

        // 拦截 OK：校验失败则不关闭对话框
        dialog.getDialogPane().lookupButton(ok).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (build() == null) {
                e.consume();
            }
        });
        dialog.setResultConverter(bt -> bt == ok ? build() : null);
    }

    Optional<PortForwardSpec> showAndWait() {
        return dialog.showAndWait();
    }

    private void updateDestState() {
        boolean dynamic = typeBox.getValue() == PortForwardSpec.Type.DYNAMIC;
        destHostField.setDisable(dynamic);
        destPortField.setDisable(dynamic);
        updateHint();
    }

    private void updateHint() {
        PortForwardSpec.Type type = typeBox.getValue();
        String key = switch (type) {
            case LOCAL -> "forward.hint.local";
            case REMOTE -> "forward.hint.remote";
            case DYNAMIC -> "forward.hint.dynamic";
        };
        hintLabel.setText(type == null ? "" : I18n.t(key));
    }

    /** 校验并构建规则；失败时设置错误文案并返回 null。 */
    private PortForwardSpec build() {
        PortForwardSpec.Type type = typeBox.getValue();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String bindHost = bindHostField.getText() == null ? "" : bindHostField.getText().trim();
        if (name.isEmpty()) {
            return fail("forward.validation.name_required");
        }
        if (bindHost.isEmpty()) {
            bindHost = "127.0.0.1";
        }
        int bindPort = parsePort(bindPortField.getText(), true);
        if (bindPort < 0) {
            return fail("forward.validation.bind_port_invalid");
        }
        String destHost = "";
        int destPort = 0;
        if (type != PortForwardSpec.Type.DYNAMIC) {
            destHost = destHostField.getText() == null ? "" : destHostField.getText().trim();
            if (destHost.isEmpty()) {
                return fail("forward.validation.dest_host_required");
            }
            destPort = parsePort(destPortField.getText(), false);
            if (destPort < 0) {
                return fail("forward.validation.dest_port_invalid");
            }
        }
        return new PortForwardSpec(name, type, bindHost, bindPort, destHost, destPort);
    }

    /** 解析端口；allowZero 时允许 0（临时端口）。非法返回 -1。 */
    private int parsePort(String text, boolean allowZero) {
        try {
            int p = Integer.parseInt(text == null ? "" : text.trim());
            int min = allowZero ? 0 : 1;
            return (p >= min && p <= 65535) ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private PortForwardSpec fail(String key) {
        errorLabel.setText(I18n.t(key));
        return null;
    }

    private static String typeKey(PortForwardSpec.Type t) {
        return switch (t) {
            case LOCAL -> "forward.type.local";
            case REMOTE -> "forward.type.remote";
            case DYNAMIC -> "forward.type.dynamic";
        };
    }
}
