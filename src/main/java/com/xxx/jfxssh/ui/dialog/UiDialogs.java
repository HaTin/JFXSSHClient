package com.xxx.jfxssh.ui.dialog;

import com.xxx.jfxssh.common.i18n.I18n;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.Optional;

/**
 * 通用对话框助手。
 *
 * <p>统一确认 / 信息 / 错误 / 文本输入 / 密码输入弹窗，文案经 {@link I18n}。</p>
 */
public final class UiDialogs {

    private UiDialogs() {
    }

    private static ButtonType okButton() {
        return new ButtonType(I18n.t("button.ok"), ButtonBar.ButtonData.OK_DONE);
    }

    private static ButtonType cancelButton() {
        return new ButtonType(I18n.t("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    /**
     * 确认对话框。
     *
     * @param titleKey 标题资源 ID
     * @param message  正文（已本地化）
     * @return 用户确认返回 true
     */
    public static boolean confirm(String titleKey, String message) {
        ButtonType ok = okButton();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, cancelButton());
        alert.setTitle(I18n.t(titleKey));
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == ok).isPresent();
    }

    /**
     * 信息提示。
     *
     * @param titleKey 标题资源 ID
     * @param message  正文（已本地化）
     */
    public static void info(String titleKey, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, okButton());
        alert.setTitle(I18n.t(titleKey));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * 错误提示。
     *
     * @param message 正文（已本地化）
     */
    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, okButton());
        alert.setTitle(I18n.t("common.error"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * 文本输入对话框。
     *
     * @param titleKey  标题资源 ID
     * @param promptKey 提示资源 ID
     * @param initial   初值
     * @return 非空输入，取消或空白返回 empty
     */
    public static Optional<String> promptText(String titleKey, String promptKey, String initial) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.t(titleKey));
        ButtonType ok = okButton();
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancelButton());

        TextField field = new TextField(initial == null ? "" : initial);
        VBox box = new VBox(8, new Label(I18n.t(promptKey)), field);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(bt -> bt == ok ? field.getText() : null);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * 密码输入对话框。
     *
     * @param title  标题（已本地化）
     * @param prompt 提示（已本地化）
     * @return 输入的密码，取消返回 empty
     */
    public static Optional<String> promptPassword(String title, String prompt) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        ButtonType ok = okButton();
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancelButton());

        PasswordField field = new PasswordField();
        VBox box = new VBox(8, new Label(prompt), field);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(bt -> bt == ok ? field.getText() : null);
        return dialog.showAndWait();
    }

    /**
     * 首次设置主密码对话框（输入 + 确认）。
     *
     * @return 主密码字符数组，取消或不合法返回 empty
     */
    public static Optional<char[]> promptNewMasterPassword() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("dialog.master.set_title"));
        ButtonType ok = okButton();
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancelButton());

        PasswordField first = new PasswordField();
        PasswordField second = new PasswordField();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(I18n.t("dialog.master.set_prompt")), first);
        grid.addRow(1, new Label(I18n.t("dialog.master.confirm_prompt")), second);
        dialog.getDialogPane().setContent(grid);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ok);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            String a = first.getText();
            String b = second.getText();
            if (a == null || a.isEmpty()) {
                error(I18n.t("dialog.master.empty"));
                e.consume();
            } else if (!a.equals(b)) {
                error(I18n.t("dialog.master.mismatch"));
                e.consume();
            }
        });
        dialog.setResultConverter(bt -> bt == ok ? first.getText().toCharArray() : null);
        return dialog.showAndWait();
    }

    /**
     * 解锁主密码对话框。
     *
     * @return 主密码字符数组，取消返回 empty
     */
    public static Optional<char[]> promptMasterPassword() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("dialog.master.unlock_title"));
        ButtonType ok = okButton();
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancelButton());

        PasswordField field = new PasswordField();
        VBox box = new VBox(8, new Label(I18n.t("dialog.master.unlock_prompt")), field);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(bt -> bt == ok ? field.getText().toCharArray() : null);
        return dialog.showAndWait();
    }

    /** 解锁对话框的用户选择。 */
    public enum UnlockAction {
        /** 用输入的主密码解锁。 */
        UNLOCK,
        /** 改用其他方式（密码 / 私钥）。 */
        OTHER,
        /** 取消。 */
        CANCEL
    }

    /**
     * 解锁请求结果。
     *
     * @param action   选择
     * @param password 主密码（仅 UNLOCK 时有效）
     */
    public record UnlockRequest(UnlockAction action, char[] password) {
    }

    /**
     * 连接时的解锁对话框：可解锁 / 改用其他方式 / 取消；密码错误可重试。
     *
     * @param showError 是否显示"主密码错误"提示（重试时）
     * @return 解锁请求
     */
    public static UnlockRequest promptMasterUnlock(boolean showError) {
        Dialog<UnlockRequest> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("dialog.master.unlock_title"));
        ButtonType unlock = new ButtonType(I18n.t("button.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType other = new ButtonType(I18n.t("dialog.master.other"), ButtonBar.ButtonData.OTHER);
        ButtonType cancel = cancelButton();
        dialog.getDialogPane().getButtonTypes().addAll(unlock, other, cancel);

        PasswordField field = new PasswordField();
        VBox box = new VBox(8);
        if (showError) {
            Label err = new Label(I18n.t("dialog.master.wrong"));
            err.setStyle("-fx-text-fill: #d33;");
            box.getChildren().add(err);
        }
        box.getChildren().addAll(new Label(I18n.t("dialog.master.unlock_prompt")), field);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(bt -> {
            if (bt == unlock) {
                return new UnlockRequest(UnlockAction.UNLOCK, field.getText().toCharArray());
            }
            if (bt == other) {
                return new UnlockRequest(UnlockAction.OTHER, null);
            }
            return new UnlockRequest(UnlockAction.CANCEL, null);
        });
        return dialog.showAndWait().orElse(new UnlockRequest(UnlockAction.CANCEL, null));
    }

    /** 认证方式选择。 */
    public enum AuthChoice {
        /** 手动输入密码。 */
        PASSWORD,
        /** 使用私钥。 */
        PRIVATE_KEY,
        /** 取消。 */
        CANCEL
    }

    /**
     * 选择认证方式（密码 / 私钥）。
     *
     * @return 选择
     */
    public static AuthChoice chooseAuthMethod() {
        ButtonType password = new ButtonType(I18n.t("auth.password"), ButtonBar.ButtonData.OK_DONE);
        ButtonType key = new ButtonType(I18n.t("auth.private_key"), ButtonBar.ButtonData.OTHER);
        ButtonType cancel = cancelButton();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("dialog.auth.choose_prompt"), password, key, cancel);
        alert.setTitle(I18n.t("dialog.auth.choose_title"));
        alert.setHeaderText(null);
        ButtonType result = alert.showAndWait().orElse(cancel);
        if (result == password) {
            return AuthChoice.PASSWORD;
        }
        if (result == key) {
            return AuthChoice.PRIVATE_KEY;
        }
        return AuthChoice.CANCEL;
    }

    /**
     * 选择私钥文件。
     *
     * @return 文件，取消返回 empty
     */
    public static Optional<File> chooseKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("dialog.connection.private_key_path"));
        return Optional.ofNullable(chooser.showOpenDialog(null));
    }

    private static final int HOST_KEY_COUNTDOWN_SECONDS = 5;

    /**
     * 主机密钥变更警告对话框。"仍然继续"按钮倒计时若干秒后才可点，强制用户阅读。
     *
     * @param host     主机
     * @param port     端口
     * @param stored   已记录指纹
     * @param received 本次收到指纹
     * @return 用户选择继续返回 true
     */
    public static boolean confirmHostKeyMismatch(String host, int port, String stored, String received) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("dialog.hostkey.title"));

        ButtonType cont = new ButtonType(I18n.t("dialog.hostkey.continue"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.t("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, cont);

        Label warning = new Label(I18n.t("dialog.hostkey.warning", host + ":" + port));
        warning.setWrapText(true);
        warning.setStyle("-fx-text-fill: #d33; -fx-font-weight: bold;");
        Label storedLabel = new Label(I18n.t("dialog.hostkey.stored", stored));
        storedLabel.setWrapText(true);
        Label receivedLabel = new Label(I18n.t("dialog.hostkey.received", received));
        receivedLabel.setWrapText(true);
        VBox box = new VBox(8, warning, storedLabel, receivedLabel);
        box.setPadding(new Insets(14));
        box.setMaxWidth(460);
        dialog.getDialogPane().setContent(box);

        Button continueButton = (Button) dialog.getDialogPane().lookupButton(cont);
        continueButton.setDisable(true);
        int[] remaining = {HOST_KEY_COUNTDOWN_SECONDS};
        continueButton.setText(I18n.t("dialog.hostkey.continue") + " (" + remaining[0] + ")");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), (ActionEvent e) -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                continueButton.setText(I18n.t("dialog.hostkey.continue"));
                continueButton.setDisable(false);
            } else {
                continueButton.setText(I18n.t("dialog.hostkey.continue") + " (" + remaining[0] + ")");
            }
        }));
        timeline.setCycleCount(HOST_KEY_COUNTDOWN_SECONDS);
        timeline.play();
        dialog.setOnHidden(e -> timeline.stop());

        dialog.setResultConverter(bt -> bt == cont);
        return Boolean.TRUE.equals(dialog.showAndWait().orElse(false));
    }
}
