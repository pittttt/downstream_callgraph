package com.downstreamcallgraph.browser;

import com.downstreamcallgraph.CallGraphDataProvider;
import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.Utils;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.Timer;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
public final class BrowserManager {
    private final Project project;
    private final JBCefBrowser browser;
    private final AtomicBoolean browserInitialized = new AtomicBoolean(false);
    private CallGraphDataProvider activeProvider;

    public BrowserManager(Project project) {
        this.project = project;
        try {
            browser = new JBCefBrowser();
            browser.loadHTML(Utils.getResourceFileAsString("build/callgraph.html"));
            createJavaScriptBridge();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static BrowserManager getInstance(Project project) {
        return project.getService(BrowserManager.class);
    }

    public JBCefBrowser getJBCefBrowser() {
        return browser;
    }

    public boolean isBrowserInitialized() {
        return browserInitialized.get();
    }

    public void executeJavaScript(String script) {
        browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
    }

    public void showMessage(String message) {
        executeJavaScript("showMessage('" + message + "')");
    }

    public void updateNetwork(String json) {
        executeJavaScript("updateNetwork(" + json + ")");
    }

    public void updateStats(int maxDepth, int totalMethods) {
        executeJavaScript("updateStats(" + maxDepth + ", " + totalMethods + ")");
    }

    public void showGraphControls() {
        executeJavaScript("showGraphControls()");
    }

    public void setGenerateMessage(String message) {
        executeJavaScript("setGenerateMessage('" + message + "')");
    }

    public void whenBrowserReady(Runnable callback) {
        if (isBrowserInitialized()) {
            callback.run();
        } else {
            final int[] attempts = {0};
            final int maxAttempts = 100;

            Timer timer = new Timer(100, e -> {
                attempts[0]++;
                if (isBrowserInitialized()) {
                    ((Timer) e.getSource()).stop();
                    callback.run();
                } else if (attempts[0] >= maxAttempts) {
                    ((Timer) e.getSource()).stop();
                    showMessage("Browser initialization timeout. Please try again.");
                }
            });
            timer.start();
        }
    }

    public void setActiveProvider(CallGraphDataProvider provider) {
        this.activeProvider = provider;
    }

    public CallGraphDataProvider getActiveProvider() {
        if (activeProvider == null) {
            return DownstreamCallGraphGenerator.getInstance(project);
        }
        return activeProvider;
    }

    public void applySettings() {
        if (isBrowserInitialized()) {
            CallGraphSettings settings = CallGraphSettings.getInstance(project);

            String backgroundColor = settings.getCustomBackgroundColor();
            if (CallGraphSettings.BACKGROUND_TYPE_IDE.equals(settings.getBackgroundType())) {
                Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
                backgroundColor = "#" + ColorUtil.toHex(editorBackground);
            }

            executeJavaScript("document.body.style.backgroundColor = '" + backgroundColor + "';");
            executeJavaScript("document.getElementById('network').style.backgroundColor = '" + backgroundColor + "';");
            executeJavaScript("updateMessageTextColor('" + backgroundColor + "')");
        }
    }

    private void createJavaScriptBridge() {
        List<JSQueryHandler> handlers = new HandlerFactory().getHandlers(browser, project);
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser loadedBrowser, CefFrame frame, int httpStatusCode) {
                super.onLoadEnd(loadedBrowser, frame, httpStatusCode);
                for (JSQueryHandler handler : handlers) {
                    injectQueryHandler(handler.getHandlerName(), handler.getJsQuery(), handler.getArgName());
                }
                browserInitialized.set(true);
                applySettings();
            }
        }, browser.getCefBrowser());
    }

    private void injectQueryHandler(String handlerName, JBCefJSQuery handler, String argName) {
        executeJavaScript("window.JavaBridge." + handlerName + " = function(" + argName + ") {" +
                handler.inject(argName) +
                "}");
    }
}
