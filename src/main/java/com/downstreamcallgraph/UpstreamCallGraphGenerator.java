package com.downstreamcallgraph;

import com.downstreamcallgraph.browser.BrowserManager;
import com.downstreamcallgraph.settings.CallGraphSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

@SuppressWarnings("unchecked")
@Service(Service.Level.PROJECT)
public final class UpstreamCallGraphGenerator implements CallGraphDataProvider {
    private final Project project;
    private final JSONArray nodes = new JSONArray();
    private final JSONArray edges = new JSONArray();
    private final JSONObject groups = new JSONObject();
    private final HashMap<Integer, PsiElement> references = new HashMap<>();
    private final List<DownstreamCallGraphGenerator.NodeInfo> nodeInfoList = new ArrayList<>();
    private final List<DownstreamCallGraphGenerator.EdgeInfo> edgeInfoList = new ArrayList<>();
    private final Map<String, Integer> methodKeyToNodeId = new HashMap<>();
    private PsiMethod lastGeneratedMethod;
    private int maxDepth = 5;
    private int nextNodeId = 1;
    private int nextEdgeId = 1;

    public UpstreamCallGraphGenerator(Project project) {
        this.project = project;
    }

    public static UpstreamCallGraphGenerator getInstance(Project project) {
        return project.getService(UpstreamCallGraphGenerator.class);
    }

    public String generate(PsiMethod targetMethod) {
        CallGraphSettings settings = CallGraphSettings.getInstance(project);
        this.maxDepth = settings.getMaxDepth();

        BrowserManager.getInstance(project).showMessage("Clearing the graph...");
        clear();

        lastGeneratedMethod = targetMethod;

        String mainKey = getMethodKey(targetMethod);
        int mainNodeId = nextNodeId++;
        methodKeyToNodeId.put(mainKey, mainNodeId);
        references.put(mainNodeId, targetMethod);

        JSONObject mainNode = createMethodNode(targetMethod, 0, mainNodeId);
        mainNode.put("shape", "ellipse");

        createGroupIfNotExists(targetMethod);
        nodes.add(mainNode);

        BrowserManager.getInstance(project).showMessage("Collecting upstream callers (depth 0/" + maxDepth + ")...");

        Set<String> visited = new HashSet<>();
        visited.add(mainKey);
        findAndAddCallers(targetMethod, mainNodeId, 0, visited);

        BrowserManager.getInstance(project).showMessage(
                "Done. Found " + nodes.size() + " methods, " + edges.size() + " calls. Rendering...");

        return getJson();
    }

    @Override
    public String getJson() {
        JSONObject graph = new JSONObject();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("groups", groups);
        return graph.toJSONString();
    }

    @Override
    public String getDirection() {
        return "UPSTREAM";
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

    private void findAndAddCallers(PsiMethod method, int currentNodeId, int depth, Set<String> visited) {
        if (depth >= maxDepth) return;

        CallGraphSettings settings = CallGraphSettings.getInstance(project);

        BrowserManager.getInstance(project).showMessage(
                "Scanning upstream depth " + depth + "/" + maxDepth
                        + " — " + nodes.size() + " methods found so far...");

        Collection<PsiReference> refs = MethodReferencesSearch.search(
                method, GlobalSearchScope.projectScope(project), false).findAll();

        for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            PsiMethod caller = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (caller == null) continue;
            if (settings.isFilterLibraryMethods() && !isProjectMethod(caller)) continue;

            PsiClass callerClass = caller.getContainingClass();
            if (callerClass != null) {
                if (settings.isMethodExcluded(callerClass.getName(), callerClass.getQualifiedName(), caller.getName())) {
                    continue;
                }
            }

            String key = getMethodKey(caller);
            Integer callerNodeId = methodKeyToNodeId.get(key);
            boolean isNew = (callerNodeId == null);
            if (isNew) {
                callerNodeId = nextNodeId++;
                methodKeyToNodeId.put(key, callerNodeId);
            }

            int edgeId = nextEdgeId++;
            references.put(edgeId, element);
            JSONObject edge = createEdge(currentNodeId, callerNodeId, element, caller, edgeId);
            edges.add(edge);

            if (isNew) {
                visited.add(key);
                references.put(callerNodeId, caller);
                JSONObject callerNode = createMethodNode(caller, depth + 1, callerNodeId);
                nodes.add(callerNode);
                createGroupIfNotExists(caller);

                findAndAddCallers(caller, callerNodeId, depth + 1, visited);
            }
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
        String className;
        if (containingClass == null) {
            className = "Unknown";
        } else if (containingClass.getQualifiedName() != null) {
            className = containingClass.getQualifiedName();
        } else if (containingClass instanceof PsiAnonymousClass) {
            PsiAnonymousClass anon = (PsiAnonymousClass) containingClass;
            className = anon.getBaseClassType().getClassName() + "$anon_" + anon.getTextOffset();
        } else {
            className = "Unknown";
        }
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

        edgeInfoList.add(new DownstreamCallGraphGenerator.EdgeInfo(fromNodeId, toNodeId, ":" + lineNumber));

        return edge;
    }

    private JSONObject createMethodNode(PsiMethod method, int depth, int nodeId) {
        JSONObject node = new JSONObject();
        node.put("id", nodeId);
        PsiClass containingClass = method.getContainingClass();
        String qualifiedName;
        String className;
        if (containingClass == null) {
            qualifiedName = "Unknown";
            className = "Unknown";
        } else if (containingClass instanceof PsiAnonymousClass) {
            PsiAnonymousClass anon = (PsiAnonymousClass) containingClass;
            qualifiedName = anon.getBaseClassType().getCanonicalText();
            className = anon.getBaseClassType().getClassName();
        } else {
            qualifiedName = containingClass.getQualifiedName() != null ? containingClass.getQualifiedName() : "Unknown";
            className = containingClass.getName() != null ? containingClass.getName() : "Unknown";
        }

        node.put("group", qualifiedName);
        node.put("title", qualifiedName + "\n" + method.getName());
        node.put("level", depth);

        String label = className + "\n" + method.getName();
        node.put("label", label);

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
        nodeInfoList.add(new DownstreamCallGraphGenerator.NodeInfo(
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
        if (qualifiedName == null && containingClass instanceof PsiAnonymousClass) {
            qualifiedName = ((PsiAnonymousClass) containingClass).getBaseClassType().getCanonicalText();
        }
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
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null && containingClass instanceof PsiAnonymousClass) {
            qualifiedName = ((PsiAnonymousClass) containingClass).getBaseClassType().getCanonicalText();
        }
        return (JSONObject) groups.get(qualifiedName);
    }

    @Override
    public PsiElement getReference(Integer id) {
        return references.get(id);
    }

    @Override
    public PsiMethod getLastGeneratedMethod() {
        return lastGeneratedMethod;
    }

    @Override
    public List<DownstreamCallGraphGenerator.NodeInfo> getNodeInfoList() {
        return nodeInfoList;
    }

    @Override
    public List<DownstreamCallGraphGenerator.EdgeInfo> getEdgeInfoList() {
        return edgeInfoList;
    }

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }
}
