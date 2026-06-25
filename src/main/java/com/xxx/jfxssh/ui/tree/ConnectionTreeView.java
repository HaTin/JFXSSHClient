package com.xxx.jfxssh.ui.tree;

import com.xxx.jfxssh.common.Constants;
import com.xxx.jfxssh.common.i18n.I18n;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * 连接树组件（见 docs/UI_DESIGN.md）。
 *
 * <p>左侧导航区，展示分组与连接的树形结构，并提供右键菜单：
 * 分组（添加 / 重命名 / 删除）、连接（连接 / 编辑 / 复制 / 删除）。</p>
 *
 * <p>本阶段仅实现界面布局：节点为占位数据，右键菜单项不绑定业务行为
 * （无 SSH、无数据库）。</p>
 */
public final class ConnectionTreeView {

    private final TreeView<String> tree;
    private final TreeItem<String> rootItem;
    private final TreeItem<String> production;
    private final TreeItem<String> testing;
    private final TreeItem<String> local;

    /**
     * 构建连接树。
     */
    public ConnectionTreeView() {
        rootItem = new TreeItem<>(I18n.t("tree.connections"));
        rootItem.setExpanded(true);

        production = new TreeItem<>(I18n.t("tree.group.production"));
        testing = new TreeItem<>(I18n.t("tree.group.testing"));
        local = new TreeItem<>(I18n.t("tree.group.local"));
        rootItem.getChildren().addAll(production, testing, local);

        tree = new TreeView<>(rootItem);
        tree.setId("ConnectionTree");
        tree.setMinWidth(150);
        tree.setPrefWidth(Constants.CONNECTION_TREE_WIDTH);
        tree.setCellFactory(tv -> new ContextTreeCell());

        // 树节点文本不是属性，无法直接绑定：监听语言变化后刷新值
        I18n.currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> refreshLabels());
    }

    /**
     * @return 树视图节点
     */
    public TreeView<String> getView() {
        return tree;
    }

    private void refreshLabels() {
        rootItem.setValue(I18n.t("tree.connections"));
        production.setValue(I18n.t("tree.group.production"));
        testing.setValue(I18n.t("tree.group.testing"));
        local.setValue(I18n.t("tree.group.local"));
    }

    private static MenuItem menuItem(String key) {
        MenuItem mi = new MenuItem();
        mi.textProperty().bind(I18n.tp(key));
        return mi;
    }

    /** 根节点右键菜单：仅添加分组。 */
    private static ContextMenu rootMenu() {
        return new ContextMenu(menuItem("tree.menu.add_group"));
    }

    /** 分组右键菜单。 */
    private static ContextMenu groupMenu() {
        return new ContextMenu(
                menuItem("tree.menu.add_group"),
                menuItem("tree.menu.rename_group"),
                menuItem("tree.menu.delete_group"));
    }

    /** 连接右键菜单。 */
    private static ContextMenu connectionMenu() {
        return new ContextMenu(
                menuItem("tree.menu.connect"),
                new SeparatorMenuItem(),
                menuItem("tree.menu.edit"),
                menuItem("tree.menu.duplicate"),
                menuItem("tree.menu.delete"));
    }

    private static int depthOf(TreeItem<?> item) {
        int depth = 0;
        TreeItem<?> parent = item.getParent();
        while (parent != null) {
            depth++;
            parent = parent.getParent();
        }
        return depth;
    }

    /**
     * 按节点层级附加对应右键菜单的单元格。
     *
     * <p>depth 0=根、1=分组、≥2=连接。当前仅有占位分组，连接菜单为未来
     * 加入连接叶子节点预留。</p>
     */
    private static final class ContextTreeCell extends TreeCell<String> {

        private final ContextMenu rootMenu = rootMenu();
        private final ContextMenu groupMenu = groupMenu();
        private final ContextMenu connectionMenu = connectionMenu();

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            setText(item);

            TreeItem<String> node = getTreeItem();
            int depth = node == null ? 0 : depthOf(node);
            if (depth == 0) {
                setContextMenu(rootMenu);
            } else if (depth == 1) {
                setContextMenu(groupMenu);
            } else {
                setContextMenu(connectionMenu);
            }
        }
    }
}
