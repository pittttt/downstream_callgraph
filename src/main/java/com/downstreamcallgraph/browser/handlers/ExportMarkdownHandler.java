package com.downstreamcallgraph.browser.handlers;

import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.Utils;
import com.downstreamcallgraph.browser.JSQueryHandler;
import com.downstreamcallgraph.export.MarkdownExporter;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Function;

public class ExportMarkdownHandler extends JSQueryHandler {
    public ExportMarkdownHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return unused -> {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            ApplicationManager.getApplication().invokeLater(() -> FileChooser.chooseFile(descriptor, project, null, (VirtualFile file) -> {
                try {
                    DownstreamCallGraphGenerator generator = DownstreamCallGraphGenerator.getInstance(project);
                    CallGraphSettings settings = CallGraphSettings.getInstance(project);
                    PsiMethod lastGeneratedMethod = generator.getLastGeneratedMethod();
                    String className = lastGeneratedMethod.getContainingClass().getName();
                    String methodName = lastGeneratedMethod.getName();

                    String markdown = MarkdownExporter.export(
                            generator.getNodeInfoList(),
                            generator.getEdgeInfoList(),
                            generator.getMaxDepth(),
                            settings.isIncludeSourceInMarkdown()
                    );

                    Utils.writeToFile(file.getPath() + "/downstream_callgraph_" + project.getName() + "_" + className + "_" + methodName + ".md", markdown);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "exportMarkdown";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "unused";
    }
}
