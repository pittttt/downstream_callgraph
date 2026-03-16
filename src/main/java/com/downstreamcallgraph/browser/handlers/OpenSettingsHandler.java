package com.downstreamcallgraph.browser.handlers;

import com.downstreamcallgraph.browser.JSQueryHandler;
import com.downstreamcallgraph.settings.CallGraphSettingsDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class OpenSettingsHandler extends JSQueryHandler {
    public OpenSettingsHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return unused -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                CallGraphSettingsDialog dialog = new CallGraphSettingsDialog(project);
                dialog.showAndGet();
            });
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "openSettings";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "unused";
    }
}
