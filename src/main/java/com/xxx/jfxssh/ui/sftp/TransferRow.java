package com.xxx.jfxssh.ui.sftp;

import com.xxx.jfxssh.common.i18n.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 底部传输列表中的一行：文件名 + 进度条 + 明细（百分比 / MB / 速度）+ 取消按钮。
 *
 * <p>每个上传 / 下载对应一行，使用独立 SFTP 通道，故多个传输可并发且互不阻塞浏览。</p>
 */
final class TransferRow {

    private final HBox node = new HBox(8);
    private final ProgressBar bar = new ProgressBar(0);
    private final Label detail = new Label();
    private final Button cancel = new Button(I18n.t("sftp.button.cancel"));
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    TransferRow(String name) {
        Label nameLabel = new Label(name);
        nameLabel.setMinWidth(140);
        nameLabel.setMaxWidth(200);
        nameLabel.setStyle("-fx-font-weight: bold;");
        bar.setPrefWidth(140);
        detail.setMinWidth(200);
        cancel.setOnAction(e -> {
            cancelled.set(true);
            cancel.setDisable(true);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        node.setAlignment(Pos.CENTER_LEFT);
        node.getChildren().addAll(nameLabel, bar, detail, spacer, cancel);
    }

    HBox node() {
        return node;
    }

    /** @return 用户是否已请求取消 */
    boolean cancelled() {
        return cancelled.get();
    }

    /** 标记取消（窗口关闭时批量调用）。 */
    void requestCancel() {
        cancelled.set(true);
    }

    /**
     * 更新进度（FX 线程）。
     *
     * @param fraction   进度 [0,1]，未知时传 {@link ProgressBar#INDETERMINATE_PROGRESS}
     * @param detailText 明细文案
     */
    void update(double fraction, String detailText) {
        bar.setProgress(fraction);
        detail.setText(detailText);
    }
}
