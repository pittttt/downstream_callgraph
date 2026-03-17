package com.downstreamcallgraph.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
@State(
        name = "DownstreamCallGraphSettings",
        storages = {@Storage("downstream-callgraph-settings.xml")}
)
public class CallGraphSettings implements PersistentStateComponent<CallGraphSettings> {
    public static final String BACKGROUND_TYPE_CUSTOM = "custom";
    public static final String BACKGROUND_TYPE_IDE = "ide";

    private String backgroundType = BACKGROUND_TYPE_CUSTOM;
    private String customBackgroundColor = "#000000";
    private int maxDepth = 5;
    private boolean filterLibraryMethods = true;
    private boolean includeConstructors = true;
    private boolean includeMethodReferences = true;
    private boolean includeSourceInMarkdown = true;
    private boolean renderVisualGraph = true;
    private String excludedMethods = "";

    public static CallGraphSettings getInstance(Project project) {
        return project.getService(CallGraphSettings.class);
    }

    @Nullable
    @Override
    public CallGraphSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CallGraphSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getBackgroundType() {
        return backgroundType;
    }

    public void setBackgroundType(String backgroundType) {
        this.backgroundType = backgroundType;
    }

    public String getCustomBackgroundColor() {
        return customBackgroundColor;
    }

    public void setCustomBackgroundColor(String customBackgroundColor) {
        this.customBackgroundColor = customBackgroundColor;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, Math.min(15, maxDepth));
    }

    public boolean isFilterLibraryMethods() {
        return filterLibraryMethods;
    }

    public void setFilterLibraryMethods(boolean filterLibraryMethods) {
        this.filterLibraryMethods = filterLibraryMethods;
    }

    public boolean isIncludeConstructors() {
        return includeConstructors;
    }

    public void setIncludeConstructors(boolean includeConstructors) {
        this.includeConstructors = includeConstructors;
    }

    public boolean isIncludeMethodReferences() {
        return includeMethodReferences;
    }

    public void setIncludeMethodReferences(boolean includeMethodReferences) {
        this.includeMethodReferences = includeMethodReferences;
    }

    public boolean isIncludeSourceInMarkdown() {
        return includeSourceInMarkdown;
    }

    public void setIncludeSourceInMarkdown(boolean includeSourceInMarkdown) {
        this.includeSourceInMarkdown = includeSourceInMarkdown;
    }

    public boolean isRenderVisualGraph() {
        return renderVisualGraph;
    }

    public void setRenderVisualGraph(boolean renderVisualGraph) {
        this.renderVisualGraph = renderVisualGraph;
    }

    public String getExcludedMethods() {
        return excludedMethods;
    }

    public void setExcludedMethods(String excludedMethods) {
        this.excludedMethods = excludedMethods != null ? excludedMethods : "";
    }

    public boolean isMethodExcluded(String simpleClassName, String qualifiedClassName, String methodName) {
        if (excludedMethods == null || excludedMethods.isEmpty()) return false;
        for (String line : excludedMethods.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int lastDot = trimmed.lastIndexOf('.');
            String classPattern;
            String methodPattern;
            if (lastDot >= 0) {
                classPattern = trimmed.substring(0, lastDot);
                methodPattern = trimmed.substring(lastDot + 1);
            } else {
                classPattern = "*";
                methodPattern = trimmed;
            }

            if (!globMatches(methodPattern, methodName)) continue;
            if (simpleClassName != null && globMatches(classPattern, simpleClassName)) return true;
            if (qualifiedClassName != null && globMatches(classPattern, qualifiedClassName)) return true;
        }
        return false;
    }

    private static boolean globMatches(String pattern, String text) {
        if (text == null) return false;
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(Pattern.quote(parts[i]));
        }
        return text.matches(regex.toString());
    }

    public String getEffectiveBackgroundColor(String ideEditorBackgroundColor) {
        if (BACKGROUND_TYPE_IDE.equals(backgroundType)) {
            return ideEditorBackgroundColor;
        }
        return customBackgroundColor;
    }
}
