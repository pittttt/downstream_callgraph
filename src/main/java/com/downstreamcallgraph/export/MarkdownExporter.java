package com.downstreamcallgraph.export;

import com.downstreamcallgraph.DownstreamCallGraphGenerator.EdgeInfo;
import com.downstreamcallgraph.DownstreamCallGraphGenerator.NodeInfo;

import java.util.*;
import java.util.stream.Collectors;

public class MarkdownExporter {

    public static String export(List<NodeInfo> nodes, List<EdgeInfo> edges, int maxDepth,
                                boolean includeSource, String direction) {
        if (nodes.isEmpty()) return "";

        NodeInfo root = nodes.get(0);
        StringBuilder sb = new StringBuilder();

        // 1. Title + metadata
        sb.append("# Call Graph: ").append(root.className).append(".").append(root.methodName).append("\n\n");
        sb.append("| Property | Value |\n|---|---|\n");
        sb.append("| Method | `").append(root.signature).append("` |\n");
        sb.append("| Direction | ").append(direction).append(" |\n");
        sb.append("| Max Depth | ").append(maxDepth).append(" |\n");
        sb.append("| Total Methods | ").append(nodes.size()).append(" |\n\n");

        // 2. ASCII call tree
        sb.append("## Call Tree\n\n```\n");
        Map<Integer, NodeInfo> nodeMap = new HashMap<>();
        for (NodeInfo node : nodes) {
            nodeMap.put(node.id, node);
        }
        Map<Integer, List<EdgeInfo>> childEdgesMap = new HashMap<>();
        for (EdgeInfo edge : edges) {
            childEdgesMap.computeIfAbsent(edge.fromId, k -> new ArrayList<>()).add(edge);
        }
        renderTree(sb, root, nodeMap, childEdgesMap, "", true, new HashSet<>());
        sb.append("```\n\n");

        // 3. Method details by level
        sb.append("## Method Details\n\n");
        int actualMaxLevel = 0;
        for (NodeInfo node : nodes) {
            if (node.level > actualMaxLevel) actualMaxLevel = node.level;
        }

        for (int level = 0; level <= actualMaxLevel; level++) {
            final int currentLevel = level;
            List<NodeInfo> levelNodes = nodes.stream()
                    .filter(n -> n.level == currentLevel)
                    .collect(Collectors.toList());
            if (levelNodes.isEmpty()) continue;

            sb.append("### Level ").append(level).append("\n\n");
            for (NodeInfo node : levelNodes) {
                sb.append("#### `").append(node.signature).append("`\n\n");
                sb.append("- **Class**: `").append(node.qualifiedClassName).append("`\n");
                sb.append("- **File**: `").append(node.filePath).append(":").append(node.lineNumber).append("`\n\n");

                if (includeSource && node.sourceCode != null && !node.sourceCode.isEmpty()) {
                    sb.append("```java\n").append(node.sourceCode).append("\n```\n\n");
                }
            }
        }

        return sb.toString();
    }

    private static void renderTree(StringBuilder sb, NodeInfo current,
                                   Map<Integer, NodeInfo> nodeMap,
                                   Map<Integer, List<EdgeInfo>> childEdgesMap,
                                   String prefix, boolean isLast,
                                   Set<Integer> visited) {
        String label = current.className + "." + current.methodName + "()";
        if (visited.contains(current.id)) {
            sb.append(prefix).append(isLast ? "\\-- " : "|-- ").append(label).append(" [circular]\n");
            return;
        }

        sb.append(prefix).append(isLast ? "\\-- " : "|-- ").append(label).append("\n");
        visited.add(current.id);

        List<EdgeInfo> childEdges = childEdgesMap.getOrDefault(current.id, Collections.emptyList());
        for (int i = 0; i < childEdges.size(); i++) {
            EdgeInfo edge = childEdges.get(i);
            NodeInfo child = nodeMap.get(edge.toId);
            if (child == null) continue;
            String childPrefix = prefix + (isLast ? "    " : "|   ");
            renderTree(sb, child, nodeMap, childEdgesMap, childPrefix,
                    i == childEdges.size() - 1, visited);
        }

        visited.remove(current.id);
    }
}
