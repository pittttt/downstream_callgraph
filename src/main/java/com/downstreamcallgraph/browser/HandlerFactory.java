package com.downstreamcallgraph.browser;

import com.downstreamcallgraph.browser.handlers.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowserBase;

import java.util.ArrayList;
import java.util.List;

public class HandlerFactory {
    public List<JSQueryHandler> getHandlers(JBCefBrowserBase browser, Project project) {
        List<JSQueryHandler> handlers = new ArrayList<>();
        handlers.add(new GenerateGraphHandler(browser, project));
        handlers.add(new GoToSourceHandler(browser, project));
        handlers.add(new SaveAsHtmlHandler(browser, project));
        handlers.add(new ExportMarkdownHandler(browser, project));
        handlers.add(new OpenSettingsHandler(browser, project));
        return handlers;
    }
}
