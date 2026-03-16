package com.downstreamcallgraph;

import com.downstreamcallgraph.browser.BrowserManager;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

@SuppressWarnings("unchecked")
@Service(Service.Level.PROJECT)
public final class DownstreamCallGraphGenerator {
    private final Project project;
    private final JSONArray nodes;
    private final JSONArray edges;
    private final JSONObject groups;
    private final HashMap<Integer, PsiElement> references = new HashMap<>();
    private final List<NodeInfo> nodeInfoList = new ArrayList<>();
    private final List<EdgeInfo> edgeInfoList = new ArrayList<>();
    private final Map<String, Integer> methodKeyToNodeId = new HashMap<>();
    private PsiMethod lastGeneratedMethod;
    private int maxDepth = 5;
    private int nextNodeId = 1;
    private int nextEdgeId = 1;

    public static class NodeInfo {
        public final int id;
        public final String className;
        public final String qualifiedClassName;
        public final String methodName;
        public final String signature;
        public final String filePath;
        public final int lineNumber;
        public final String sourceCode;
        public final int level;

        public NodeInfo(int id, String className, String qualifiedClassName, String methodName,
                        String signature, String filePath, int lineNumber, String sourceCode, int level) {
            this.id = id;
            this.className = className;
            this.qualifiedClassName = qualifiedClassName;
            this.methodName = methodName;
            this.signature = signature;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.sourceCode = sourceCode;
            this.level = level;
        }
    }

    public static class EdgeInfo {
        public final int fromId;
        public final int toId;
        public final String label;

        public EdgeInfo(int fromId, int toId, String label) {
            this.fromId = fromId;
            this.toId = toId;
            this.label = label;
        }
    }

    public DownstreamCallGraphGenerator(Project project) {
        this.project = project;
        this.nodes = new JSONArray();
        this.edges = new JSONArray();
        this.groups = new JSONObject();
    }

    public static DownstreamCallGraphGenerator getInstance(Project project) {
        return project.getService(DownstreamCallGraphGenerator.class);
    }

    public String generate(PsiMethod mainMethod) {
        CallGraphSettings settings = CallGraphSettings.getInstance(project);
        this.maxDepth = settings.getMaxDepth();

        BrowserManager.getInstance(project).showMessage("Clearing the graph...");
        clear();

        lastGeneratedMethod = mainMethod;

        String mainKey = getMethodKey(mainMethod);
        int mainNodeId = nextNodeId++;
        methodKeyToNodeId.put(mainKey, mainNodeId);
        references.put(mainNodeId, mainMethod);

        JSONObject mainNode = createMethodNode(mainMethod, 0, mainNodeId);
        mainNode.put("shape", "ellipse");

        createGroupIfNotExists(mainMethod);
        nodes.add(mainNode);

        BrowserManager.getInstance(project).showMessage("Collecting downstream callees (depth 0/" + maxDepth + ")...");

        Set<String> visited = new HashSet<>();
        visited.add(mainKey);
        findAndAddCallees(mainMethod, mainNodeId, 0, visited);

        BrowserManager.getInstance(project).showMessage(
                "Done. Found " + nodes.size() + " methods, " + edges.size() + " calls. Rendering...");

        return getJson();
    }

    public String getJson() {
        JSONObject graph = new JSONObject();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("groups", groups);
        return graph.toJSONString();
    }

    private void clear() {
        nodes.clear();
        edges.clear();
        groups.clear();
        references.clear();
        nodeInfoList.clear();
        edgeInfoList.clear();
        methodKeyToNodeId.clear();
        nextNodeId = 1;
        nextEdgeId = 1;
    }

    private void findAndAddCallees(PsiMethod method, int callerNodeId, int depth, Set<String> visited) {
        if (depth >= maxDepth) return;

        CallGraphSettings settings = CallGraphSettings.getInstance(project);
        PsiCodeBlock body = method.getBody();
        if (body == null) return;

        BrowserManager.getInstance(project).showMessage(
                "Scanning depth " + depth + "/" + maxDepth
                + " — " + nodes.size() + " methods found so far...");

        // 1. Regular method calls: method()
        Collection<PsiMethodCallExpression> calls =
                PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression.class);
        for (PsiMethodCallExpression call : calls) {
            PsiMethod callee = call.resolveMethod();
            if (callee == null) continue;
            if (settings.isFilterLibraryMethods() && !isProjectMethod(callee)) continue;

            processCallee(method, callerNodeId, callee, call, depth, visited);
        }

        // 2. Constructor calls: new Foo()
        if (settings.isIncludeConstructors()) {
            Collection<PsiNewExpression> newExprs =
                    PsiTreeUtil.findChildrenOfType(body, PsiNewExpression.class);
            for (PsiNewExpression newExpr : newExprs) {
                PsiMethod constructor = newExpr.resolveConstructor();
                if (constructor == null) continue;
                if (settings.isFilterLibraryMethods() && !isProjectMethod(constructor)) continue;

                processCallee(method, callerNodeId, constructor, newExpr, depth, visited);
            }
        }

        // 3. Method references: Foo::bar
        if (settings.isIncludeMethodReferences()) {
            Collection<PsiMethodReferenceExpression> methodRefs =
                    PsiTreeUtil.findChildrenOfType(body, PsiMethodReferenceExpression.class);
            for (PsiMethodReferenceExpression methodRef : methodRefs) {
                PsiElement resolved = methodRef.resolve();
                if (!(resolved instanceof PsiMethod)) continue;
                PsiMethod callee = (PsiMethod) resolved;
                if (settings.isFilterLibraryMethods() && !isProjectMethod(callee)) continue;

                processCallee(method, callerNodeId, callee, methodRef, depth, visited);
            }
        }
    }

    private void processCallee(PsiMethod caller, int callerNodeId, PsiMethod callee,
                               PsiElement callSite, int depth, Set<String> visited) {
        String key = getMethodKey(callee);

        // Determine callee node ID (reuse if already created)
        Integer calleeNodeId = methodKeyToNodeId.get(key);
        boolean isNew = (calleeNodeId == null);
        if (isNew) {
            calleeNodeId = nextNodeId++;
            methodKeyToNodeId.put(key, calleeNodeId);
        }

        // Always create the edge
        int edgeId = nextEdgeId++;
        references.put(edgeId, callSite);
        JSONObject edge = createEdge(callerNodeId, calleeNodeId, callSite, caller, edgeId);
        edges.add(edge);

        if (isNew) {
            visited.add(key);
            references.put(calleeNodeId, callee);
            JSONObject calleeNode = createMethodNode(callee, depth + 1, calleeNodeId);
            nodes.add(calleeNode);
            createGroupIfNotExists(callee);

            findAndAddCallees(callee, calleeNodeId, depth + 1, visited);
        }
    }

    private boolean isProjectMethod(PsiMethod method) {
        PsiFile containingFile = method.getContainingFile();
        if (containingFile == null) return false;
        VirtualFile file = containingFile.getVirtualFile();
        return file != null && file.isInLocalFileSystem();
    }

    private String getMethodKey(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getQualifiedName() : "Unknown";
        StringBuilder params = new StringBuilder();
        for (PsiParameter param : method.getParameterList().getParameters()) {
            if (params.length() > 0) params.append(",");
            params.append(param.getType().getCanonicalText());
        }
        return className + "#" + method.getName() + "(" + params + ")";
    }

    @NotNull
    private JSONObject createEdge(int fromNodeId, int toNodeId, PsiElement callSite,
                                  PsiMethod caller, int edgeId) {
        JSONObject edge = new JSONObject();
        edge.put("id", edgeId);
        edge.put("from", fromNodeId);
        edge.put("to", toNodeId);

        PsiFile file = callSite.getContainingFile();
        int lineNumber = 0;
        if (file != null) {
            Document document = file.getViewProvider().getDocument();
            int textOffset = callSite.getTextOffset();
            if (document != null && textOffset >= 0 && textOffset <= document.getTextLength()) {
                lineNumber = document.getLineNumber(textOffset) + 1;
            }
        }
        edge.put("label", ":" + lineNumber);

        JSONObject group = getGroup(caller);
        if (group != null) {
            edge.put("font", group.get("font"));
        }

        edgeInfoList.add(new EdgeInfo(fromNodeId, toNodeId, ":" + lineNumber));

        return edge;
    }

    private JSONObject createMethodNode(PsiMethod method, int depth, int nodeId) {
        JSONObject node = new JSONObject();
        node.put("id", nodeId);
        PsiClass containingClass = method.getContainingClass();
        String qualifiedName = containingClass != null ? containingClass.getQualifiedName() : "Unknown";
        String className = containingClass != null ? containingClass.getName() : "Unknown";

        node.put("group", qualifiedName);
        node.put("title", qualifiedName + "\n" + method.getName());
        node.put("level", depth);

        String label = className + "\n" + method.getName();
        node.put("label", label);

        // Collect node info for markdown export
        String signature = getMethodSignature(method);
        String filePath = "";
        int lineNumber = 0;
        PsiFile psiFile = method.getContainingFile();
        if (psiFile != null) {
            VirtualFile vf = psiFile.getVirtualFile();
            if (vf != null) {
                filePath = vf.getPath();
            }
            Document doc = psiFile.getViewProvider().getDocument();
            int textOffset = method.getTextOffset();
            if (doc != null && textOffset >= 0 && textOffset <= doc.getTextLength()) {
                lineNumber = doc.getLineNumber(textOffset) + 1;
            }
        }

        String sourceCode = method.getText();
        nodeInfoList.add(new NodeInfo(
                nodeId, className, qualifiedName, method.getName(),
                signature, filePath, lineNumber, sourceCode, depth
        ));

        return node;
    }

    private String getMethodSignature(PsiMethod method) {
        StringBuilder sb = new StringBuilder();
        PsiType returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getPresentableText()).append(" ");
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            sb.append(containingClass.getName()).append(".");
        }
        sb.append(method.getName()).append("(");
        PsiParameter[] params = method.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getType().getPresentableText()).append(" ").append(params[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }

    private void createGroupIfNotExists(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return;
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null || groups.containsKey(qualifiedName)) return;

        JSONObject group = new JSONObject();
        UIColor uiColor = UIColor.getNextColor();

        JSONObject color = new JSONObject();
        color.put("background", uiColor.getMainColor());
        color.put("border", uiColor.getMainColor());

        JSONObject hoverAndHighlightColor = new JSONObject();
        hoverAndHighlightColor.put("background", uiColor.getDarkColor());
        hoverAndHighlightColor.put("border", uiColor.getDarkColor());

        color.put("highlight", hoverAndHighlightColor);
        color.put("hover", hoverAndHighlightColor);

        JSONObject font = new JSONObject();
        font.put("color", uiColor.getTextColor());
        font.put("strokeColor", uiColor.getMainColor());

        group.put("color", color);
        group.put("font", font);

        groups.put(qualifiedName, group);
    }

    private JSONObject getGroup(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return null;
        return (JSONObject) groups.get(containingClass.getQualifiedName());
    }

    public PsiElement getReference(Integer id) {
        return references.get(id);
    }

    public PsiMethod getLastGeneratedMethod() {
        return lastGeneratedMethod;
    }

    public List<NodeInfo> getNodeInfoList() {
        return nodeInfoList;
    }

    public List<EdgeInfo> getEdgeInfoList() {
        return edgeInfoList;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
