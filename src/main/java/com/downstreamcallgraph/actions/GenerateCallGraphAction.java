package com.downstreamcallgraph.actions;

import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.Utils;
import com.downstreamcallgraph.browser.BrowserManager;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class GenerateCallGraphAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        if (DumbService.isDumb(project)) {
            Messages.showInfoMessage(project,
                    "Cannot generate call graph while indexing is in progress.\nPlease wait for indexing to complete.",
                    "Downstream Call Graph");
            return;
        }

        PsiMethod method = Utils.getMethodAtCaret(project, e.getData(CommonDataKeys.EDITOR));
        if (method == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DownstreamCallGraph");
        if (toolWindow != null) {
            toolWindow.show(() -> generateCallGraph(project, method));
        }
    }

    private void generateCallGraph(Project project, PsiMethod method) {
        BrowserManager browserManager = BrowserManager.getInstance(project);

        browserManager.whenBrowserReady(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Downstream Call Graph") {
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    ApplicationManager.getApplication().runReadAction(() -> {
                        browserManager.showMessage("Generating downstream call graph for " + method.getName() + "...");
                        DownstreamCallGraphGenerator generator = DownstreamCallGraphGenerator.getInstance(project);
                        String graph = generator.generate(method);
                        browserManager.setActiveProvider(generator);
                        CallGraphSettings settings = CallGraphSettings.getInstance(project);
                        if (settings.isRenderVisualGraph()) {
                            browserManager.showMessage("Sending graph to embedded browser...");
                            browserManager.updateNetwork(graph);
                        } else {
                            browserManager.showMessage("Graph data generated ("
                                    + generator.getNodeInfoList().size() + " methods, "
                                    + generator.getEdgeInfoList().size() + " calls). "
                                    + "Visual rendering is disabled. Use Export Markdown to get results.");
                            browserManager.updateStats(generator.getMaxDepth(), generator.getNodeInfoList().size());
                            browserManager.showGraphControls();
                        }
                        browserManager.setGenerateMessage("+FOR " + method.getName());
                    });
                }
            });
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        e.getPresentation().setVisible(true);

        if (DumbService.isDumb(project)) {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setDescription("Cannot generate call graph while indexing is in progress");
            return;
        }

        PsiMethod method = Utils.getMethodAtCaret(project, e.getData(CommonDataKeys.EDITOR));
        e.getPresentation().setEnabled(method != null);
    }
}
