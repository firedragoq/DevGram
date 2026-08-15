package org.telegram.messenger;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

/** Local-only development endpoint for installing a .dgplugin over ADB. */
public final class DevGramDevServer extends NanoHTTPD {
    private static DevGramDevServer instance;
    private final String token;

    private DevGramDevServer(int port, String token) { super("127.0.0.1", port); this.token = token; }

    public static synchronized boolean start(int port, String token) {
        try {
            stopServer();
            instance = new DevGramDevServer(port, token == null ? "" : token);
            instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            return true;
        } catch (Throwable e) { FileLog.e(e); instance = null; return false; }
    }

    public static synchronized void stopServer() {
        if (instance != null) { try { instance.stop(); } catch (Throwable ignore) {} instance = null; }
    }

    public static synchronized boolean isRunning() { return instance != null; }

    @Override public Response serve(IHTTPSession session) {
        if (!"127.0.0.1".equals(session.getRemoteIpAddress()) && !"localhost".equals(session.getRemoteIpAddress()))
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "local only");
        String supplied = session.getHeaders().get("x-devgram-token");
        if (token.length() > 0 && (supplied == null || !token.equals(supplied)))
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "bad token");
        if (Method.POST.equals(session.getMethod()) && "/upload".equals(session.getUri())) {
            try {
                Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                String temp = files.get("postData");
                if (temp == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "empty body");
                File target = new File(DevGramPlugins.pluginsDir(), "devserver-upload.dgplugin");
                java.nio.file.Files.copy(new File(temp).toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                boolean ok = DevGramPlugins.installPackage(target.getAbsolutePath(), null, true);
                int count = DevGramPlugins.reload();
                return newFixedLengthResponse(ok ? Response.Status.OK : Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "installed=" + ok + ";reloaded=" + count);
            } catch (Throwable e) { FileLog.e(e); return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error"); }
        }
        if (Method.POST.equals(session.getMethod()) && "/reload".equals(session.getUri())) {
            String pluginId = session.getParms().get("plugin");
            int count = pluginId == null || pluginId.isEmpty() ? DevGramPlugins.reload() : DevGramPlugins.reloadPlugin(pluginId);
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "reloaded=" + count);
        }
        if (Method.POST.equals(session.getMethod()) && "/debugger/start".equals(session.getUri())) {
            String platform = session.getParms().get("platform");
            String host = session.getParms().get("host");
            String portText = session.getParms().get("port");
            int port = 5678;
            try { if (portText != null) port = Integer.parseInt(portText); } catch (Throwable ignore) { }
            if (platform == null || platform.isEmpty()) platform = "vscode";
            if (host == null || host.isEmpty()) host = "127.0.0.1";
            String result = DevGramPlugins.startRemoteDebugger(platform, host, port);
            return newFixedLengthResponse(result.startsWith("error") ? Response.Status.INTERNAL_ERROR : Response.Status.OK,
                    MIME_PLAINTEXT, "debugger=" + result);
        }
        if (Method.POST.equals(session.getMethod()) && "/debugger/stop".equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "debugger=" + DevGramPlugins.stopRemoteDebugger());
        }
        if (Method.GET.equals(session.getMethod()) && "/status".equals(session.getUri()))
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "ok;debugger=" + DevGramPlugins.remoteDebuggerStatus());
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
    }
}
