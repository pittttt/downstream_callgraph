package com.downstreamcallgraph.api;

import com.downstreamcallgraph.CallGraphDataProvider;
import com.downstreamcallgraph.DownstreamCallGraphGenerator;
import com.downstreamcallgraph.UpstreamCallGraphGenerator;
import com.downstreamcallgraph.export.MarkdownExporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.ide.HttpRequestHandler;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public class CallGraphHttpHandler extends HttpRequestHandler {

    private static final String API_PREFIX = "/api/callgraph";

    @Override
    public boolean isSupported(FullHttpRequest request) {
        return request.uri().startsWith(API_PREFIX);
    }

    @Override
    public boolean process(QueryStringDecoder urlDecoder,
                           FullHttpRequest request,
                           ChannelHandlerContext context) {
        String path = urlDecoder.path();

        if (path.equals(API_PREFIX + "/health")) {
            JSONObject resp = new JSONObject();
            resp.put("status", "ok");
            sendJsonResponse(context, HttpResponseStatus.OK, resp.toJSONString());
            return true;
        }

        if (path.equals(API_PREFIX + "/generate")) {
            handleGenerate(urlDecoder, context);
            return true;
        }

        sendErrorResponse(context, HttpResponseStatus.NOT_FOUND, "Unknown endpoint: " + path);
        return true;
    }

    private void handleGenerate(QueryStringDecoder urlDecoder, ChannelHandlerContext context) {
        String className = getParam(urlDecoder, "className");
        String methodName = getParam(urlDecoder, "method");
        String direction = getParam(urlDecoder, "direction", "downstream");
        String format = getParam(urlDecoder, "format", "markdown");
        boolean includeSource = !"false".equals(getParam(urlDecoder, "includeSource", "true"));

        if (className == null || className.isEmpty()) {
            sendErrorResponse(context, HttpResponseStatus.BAD_REQUEST,
                    "Missing required parameter: className");
            return;
        }
        if (methodName == null || methodName.isEmpty()) {
            sendErrorResponse(context, HttpResponseStatus.BAD_REQUEST,
                    "Missing required parameter: method");
            return;
        }

        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        ApplicationManager.getApplication().runReadAction(() -> {
            Project project = findProjectWithClass(className);
            if (project == null) {
                errorRef.set("Class not found in any open project: " + className);
                return;
            }

            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(className, GlobalSearchScope.projectScope(project));
            if (psiClass == null) {
                errorRef.set("Class not found: " + className);
                return;
            }

            PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
            if (methods.length == 0) {
                errorRef.set("Method '" + methodName + "' not found in class " + className);
                return;
            }

            PsiMethod targetMethod = methods[0];
            CallGraphDataProvider provider;

            if ("upstream".equalsIgnoreCase(direction)) {
                UpstreamCallGraphGenerator gen = UpstreamCallGraphGenerator.getInstance(project);
                gen.generate(targetMethod, true);
                provider = gen;
            } else {
                DownstreamCallGraphGenerator gen = DownstreamCallGraphGenerator.getInstance(project);
                gen.generate(targetMethod, true);
                provider = gen;
            }

            if ("json".equalsIgnoreCase(format)) {
                resultRef.set(provider.getJson());
            } else {
                resultRef.set(MarkdownExporter.export(
                        provider.getNodeInfoList(),
                        provider.getEdgeInfoList(),
                        provider.getMaxDepth(),
                        includeSource,
                        provider.getDirection()
                ));
            }
        });

        String error = errorRef.get();
        if (error != null) {
            sendErrorResponse(context, HttpResponseStatus.NOT_FOUND, error);
            return;
        }

        String result = resultRef.get();
        if (result == null) {
            sendErrorResponse(context, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate call graph");
            return;
        }

        String contentType = "json".equalsIgnoreCase(format)
                ? "application/json; charset=UTF-8"
                : "text/markdown; charset=UTF-8";
        sendResponse(context, HttpResponseStatus.OK, result, contentType);
    }

    private Project findProjectWithClass(String className) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;
            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(className, GlobalSearchScope.projectScope(project));
            if (psiClass != null) return project;
        }
        return null;
    }

    private String getParam(QueryStringDecoder decoder, String name) {
        return getParam(decoder, name, null);
    }

    private String getParam(QueryStringDecoder decoder, String name, String defaultValue) {
        List<String> values = decoder.parameters().get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return defaultValue;
    }

    private void sendJsonResponse(ChannelHandlerContext context,
                                  HttpResponseStatus status, String json) {
        sendResponse(context, status, json, "application/json; charset=UTF-8");
    }

    private void sendErrorResponse(ChannelHandlerContext context,
                                   HttpResponseStatus status, String message) {
        JSONObject error = new JSONObject();
        error.put("error", message);
        sendJsonResponse(context, status, error.toJSONString());
    }

    private void sendResponse(ChannelHandlerContext context,
                              HttpResponseStatus status, String body, String contentType) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set("Content-Type", contentType);
        response.headers().set("Content-Length", bytes.length);
        response.headers().set("Access-Control-Allow-Origin", "*");
        context.writeAndFlush(response);
    }
}
