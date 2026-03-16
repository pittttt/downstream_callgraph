package com.downstreamcallgraph;

import com.downstreamcallgraph.browser.BrowserManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class CallGraphCaretListener implements CaretListener {
    private final Project project;
    private final ToolWindow toolWindow;

    public CallGraphCaretListener(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
    }

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        if (toolWindow == null || !toolWindow.isVisible() || project == null) {
            return;
        }

        Editor editor = event.getEditor();
        if (editor == null) {
            return;
        }

        if (editor.getProject() != null && editor.getProject().equals(project)) {
            if (DumbService.isDumb(project)) {
                return;
            }

            PsiMethod method = Utils.getMethodAtCaret(project, editor);

            BrowserManager browserManager = BrowserManager.getInstance(project);
            if (browserManager != null) {
                if (method != null) {
                    browserManager.setGenerateMessage("+FOR " + method.getName());
                } else {
                    browserManager.setGenerateMessage("-PLACE YOUR CARET ON A METHOD");
                }
            }
        }
    }
}
