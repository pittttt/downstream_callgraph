package com.downstreamcallgraph.browser.handlers;

import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.Utils;
import com.downstreamcallgraph.browser.BrowserManager;
import com.downstreamcallgraph.browser.JSQueryHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class GenerateGraphHandler extends JSQueryHandler {
    public GenerateGraphHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return unused -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (DumbService.isDumb(project)) {
                    BrowserManager browserManager = BrowserManager.getInstance(project);
                    if (browserManager != null) {
                        browserManager.showMessage("Cannot generate call graph while indexing is in progress. Please wait for indexing to complete.");
                    }
                    return;
                }

                PsiMethod method = Utils.getMethodAtCaret(project, null);
                if (method == null) {
                    return;
                }

                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Downstream Call Graph") {
                    public void run(@NotNull ProgressIndicator progressIndicator) {
                        ApplicationManager.getApplication().runReadAction(() -> {
                            String graph = DownstreamCallGraphGenerator.getInstance(project).generate(method);
                            BrowserManager browserManager = BrowserManager.getInstance(project);
                            browserManager.showMessage("Sending graph to embedded browser...");
                            browserManager.updateNetwork(graph);
                        });
                    }
                });
            });
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "generateGraph";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "unused";
    }
}
