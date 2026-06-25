package com.xxx.jfxssh.ui.dialog;

import com.xxx.jfxssh.common.i18n.I18n;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

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
        TextInputDialog dialog = new TextInputDialog(initial == null ? "" : initial);
        dialog.setTitle(I18n.t(titleKey));
        dialog.setHeaderText(null);
        dialog.setContentText(I18n.t(promptKey));
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
}
