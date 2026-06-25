package com.xxx.jfxssh.ui.status;

import com.xxx.jfxssh.common.i18n.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * 状态栏（见 docs/UI_DESIGN.md）。
 *
 * <p>底部状态栏，高度 24px，显示「状态 | 编码 | 协议」。本阶段仅实现界面
 * 布局：状态为静态占位文案，不反映真实连接状态。</p>
 */
public final class StatusBar {

    private static final double HEIGHT = 24;

    private final HBox bar;

    /**
     * 构建状态栏。
     */
    public StatusBar() {
        Label left = new Label();
        left.textProperty().bind(I18n.tp("status.ready"));

        Label middle = new Label("UTF-8");
        Label right = new Label("SSH");

        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        bar = new HBox(left, spacerLeft, middle, spacerRight, right);
        bar.getStyleClass().add("status-bar");
        bar.setPrefHeight(HEIGHT);
        bar.setMinHeight(HEIGHT);
        bar.setPadding(new Insets(0, 12, 0, 12));
        bar.setSpacing(8);
        bar.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * @return 状态栏节点
     */
    public HBox getView() {
        return bar;
    }
}
