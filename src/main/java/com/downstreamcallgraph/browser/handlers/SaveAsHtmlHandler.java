package com.downstreamcallgraph.browser.handlers;

import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.Utils;
import com.downstreamcallgraph.browser.JSQueryHandler;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.IOException;
import java.util.function.Function;

public class SaveAsHtmlHandler extends JSQueryHandler {
    public SaveAsHtmlHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return unused -> {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            ApplicationManager.getApplication().invokeLater(() -> FileChooser.chooseFile(descriptor, project, null, (VirtualFile file) -> {
                try {
                    String saveAsTemplate = Utils.getResourceFileAsString("build/saveas.html");
                    PsiMethod lastGeneratedMethod = DownstreamCallGraphGenerator.getInstance(project).getLastGeneratedMethod();
                    String className = lastGeneratedMethod.getContainingClass().getName();
                    String methodName = lastGeneratedMethod.getName();
                    String methodPath = className + "." + methodName;
                    String title = "Downstream Call Graph of " + project.getName() + " - " + methodPath;

                    CallGraphSettings settings = CallGraphSettings.getInstance(project);
                    String backgroundColor = settings.getCustomBackgroundColor();
                    if (CallGraphSettings.BACKGROUND_TYPE_IDE.equals(settings.getBackgroundType())) {
                        Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
                        backgroundColor = "#" + ColorUtil.toHex(editorBackground);
                    }

                    saveAsTemplate = saveAsTemplate.replace("${title}", title);
                    saveAsTemplate = saveAsTemplate.replace("background: black;", "background: " + backgroundColor + ";");
                    saveAsTemplate += "<script>updateNetwork(" + DownstreamCallGraphGenerator.getInstance(project).getJson() + ")</script>";
                    Utils.writeToFile(file.getPath() + "/downstream_callgraph_" + project.getName() + "_" + className + "_" + methodName + ".html", saveAsTemplate);
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
        return "saveAsHtml";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "unused";
    }
}
