package com.xxx.jfxssh.ui.dialog;

import com.xxx.jfxssh.ssh.HostKeyPrompt;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * {@link HostKeyPrompt} 的 JavaFX 实现：把后台 SSH 线程的请求转到 FX 线程弹窗，
 * 并阻塞等待用户选择（见 {@link UiDialogs#confirmHostKeyMismatch}）。
 */
public final class FxHostKeyPrompt implements HostKeyPrompt {

    @Override
    public boolean onMismatch(String host, int port, String storedFingerprint, String receivedFingerprint) {
        if (Platform.isFxApplicationThread()) {
            return UiDialogs.confirmHostKeyMismatch(host, port, storedFingerprint, receivedFingerprint);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> future.complete(
                UiDialogs.confirmHostKeyMismatch(host, port, storedFingerprint, receivedFingerprint)));
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }
}
