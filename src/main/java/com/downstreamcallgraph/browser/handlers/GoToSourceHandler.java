package com.downstreamcallgraph.browser.handlers;

import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.browser.BrowserManager;
import com.downstreamcallgraph.browser.JSQueryHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class GoToSourceHandler extends JSQueryHandler {
    public GoToSourceHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return nodeHashCode -> {
            if (DumbService.isDumb(project)) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    BrowserManager.getInstance(project).showMessage("Cannot navigate to source while indexing is in progress.");
                });
                return null;
            }

            PsiElement element = DownstreamCallGraphGenerator.getInstance(project).getReference(Integer.parseInt(nodeHashCode));
            if (element != null) {
                ApplicationManager.getApplication().invokeLater(() -> NavigationUtil.activateFileWithPsiElement(element));
            }
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "goToSource";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "nodeHashCode";
    }
}
