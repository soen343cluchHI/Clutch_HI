package sugarrush.presentation.http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import sugarrush.application.SugarRush;

public final class ApiServer {
    private final SugarRush application;

    private ApiServer(SugarRush application) {
        this.application = application;
    }

    public static void main(String[] args) throws IOException {
        Path seed = Path.of(args.length > 0
                ? args[0] : "backend/config/demo.properties");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        ApiServer api = new ApiServer(SugarRush.fromSeed(seed));
        HttpServer server = api.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        System.out.println("SugarRush backend: http://localhost:" + port);
        System.out.println("Connection check: http://localhost:" + port + "/api/health");
        System.out.println("Press Ctrl+C to stop the backend.");
    }

    private HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", this::handle);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"/api/health".equals(exchange.getRequestURI().getPath())) {
                sendJson(exchange, 404, "{\"error\":\"Endpoint not found\"}");
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJson(exchange, 405, "{\"error\":\"Use GET for this endpoint\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"SugarRush backend\"}");
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
