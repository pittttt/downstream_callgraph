package com.downstreamcallgraph;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.List;

public interface CallGraphDataProvider {
    PsiElement getReference(Integer id);
    PsiMethod getLastGeneratedMethod();
    List<DownstreamCallGraphGenerator.NodeInfo> getNodeInfoList();
    List<DownstreamCallGraphGenerator.EdgeInfo> getEdgeInfoList();
    int getMaxDepth();
    String getJson();
    String getDirection();
}
