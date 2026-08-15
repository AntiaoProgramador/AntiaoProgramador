package com.antiao.nf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new StaticHandler());
        server.createContext("/api/invoices", new InvoiceApiHandler());
        server.createContext("/api/invoices/", new InvoiceApiHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Servidor iniciado em http://localhost:" + port);
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                writeResource(exchange, "/web/index.html", "text/html; charset=utf-8");
                return;
            }
            if ("/style.css".equals(path)) {
                writeResource(exchange, "/web/style.css", "text/css; charset=utf-8");
                return;
            }
            if ("/app.js".equals(path)) {
                writeResource(exchange, "/web/app.js", "application/javascript; charset=utf-8");
                return;
            }
            sendText(exchange, 404, "Not found");
        }
    }

    static class InvoiceApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("GET".equals(method) && "/api/invoices".equals(path)) {
                    listInvoices(exchange);
                    return;
                }
                if ("POST".equals(method) && "/api/invoices".equals(path)) {
                    createInvoice(exchange);
                    return;
                }

                Long invoiceId = extractInvoiceId(path);
                if (invoiceId == null) {
                    sendJson(exchange, 404, Map.of("error", "Rota não encontrada"));
                    return;
                }

                if ("POST".equals(method) && path.endsWith("/confirm")) {
                    confirmInvoice(exchange, invoiceId);
                    return;
                }
                if ("POST".equals(method) && path.endsWith("/disagree")) {
                    disagreeInvoice(exchange, invoiceId);
                    return;
                }

                sendJson(exchange, 405, Map.of("error", "Método não permitido"));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private void listInvoices(HttpExchange exchange) throws Exception {
            List<Map<String, Object>> invoices = new ArrayList<>();
            String sql = "SELECT id, invoice_number, issuer, total_amount, description, created_at FROM invoices ORDER BY id DESC";
            try (Connection conn = Database.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    invoices.add(Map.of(
                            "id", id,
                            "invoiceNumber", rs.getString("invoice_number"),
                            "issuer", rs.getString("issuer"),
                            "totalAmount", rs.getBigDecimal("total_amount"),
                            "description", rs.getString("description"),
                            "createdAt", rs.getTimestamp("created_at").toString(),
                            "systems", fetchSystemsStatus(conn, id),
                            "disagreements", fetchDisagreements(conn, id)
                    ));
                }
            }
            sendJson(exchange, 200, invoices);
        }

        private List<Map<String, Object>> fetchSystemsStatus(Connection conn, long invoiceId) throws Exception {
            List<Map<String, Object>> result = new ArrayList<>();
            String sql = """
                    SELECT s.name, st.status, st.notes, st.updated_at
                    FROM systems s
                    LEFT JOIN invoice_system_status st
                      ON st.system_id = s.id AND st.invoice_id = ?
                    ORDER BY s.id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(Map.of(
                                "system", rs.getString("name"),
                                "status", rs.getString("status") == null ? "PENDING" : rs.getString("status"),
                                "notes", rs.getString("notes") == null ? "" : rs.getString("notes")
                        ));
                    }
                }
            }
            return result;
        }

        private List<Map<String, Object>> fetchDisagreements(Connection conn, long invoiceId) throws Exception {
            List<Map<String, Object>> result = new ArrayList<>();
            String sql = "SELECT system_name, reason, details, created_at FROM invoice_disagreements WHERE invoice_id = ? ORDER BY id DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(Map.of(
                                "system", rs.getString("system_name"),
                                "reason", rs.getString("reason"),
                                "details", rs.getString("details") == null ? "" : rs.getString("details"),
                                "createdAt", rs.getTimestamp("created_at").toString()
                        ));
                    }
                }
            }
            return result;
        }

        private void createInvoice(HttpExchange exchange) throws Exception {
            JsonObject json = GSON.fromJson(readBody(exchange), JsonObject.class);
            String number = json.get("invoiceNumber").getAsString();
            String issuer = json.get("issuer").getAsString();
            String description = json.has("description") ? json.get("description").getAsString() : "";
            double totalAmount = json.get("totalAmount").getAsDouble();

            try (Connection conn = Database.getConnection()) {
                conn.setAutoCommit(false);
                long invoiceId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO invoices (invoice_number, issuer, total_amount, description) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, number);
                    ps.setString(2, issuer);
                    ps.setDouble(3, totalAmount);
                    ps.setString(4, description);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        invoiceId = keys.getLong(1);
                    }
                }

                String statusSql = "INSERT INTO invoice_system_status (invoice_id, system_id, status, notes) SELECT ?, id, 'PENDING', '' FROM systems";
                try (PreparedStatement ps = conn.prepareStatement(statusSql)) {
                    ps.setLong(1, invoiceId);
                    ps.executeUpdate();
                }

                conn.commit();
                sendJson(exchange, 201, Map.of("id", invoiceId, "message", "Nota fiscal criada"));
            }
        }

        private void confirmInvoice(HttpExchange exchange, long invoiceId) throws Exception {
            JsonObject json = GSON.fromJson(readBody(exchange), JsonObject.class);
            String systemName = json.get("system").getAsString();
            String notes = json.has("notes") ? json.get("notes").getAsString() : "";

            try (Connection conn = Database.getConnection()) {
                String sql = """
                        UPDATE invoice_system_status st
                        JOIN systems s ON s.id = st.system_id
                        SET st.status = 'CONFIRMED', st.notes = ?
                        WHERE st.invoice_id = ? AND s.name = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, notes);
                    ps.setLong(2, invoiceId);
                    ps.setString(3, systemName);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        sendJson(exchange, 404, Map.of("error", "Sistema ou nota não encontrado"));
                        return;
                    }
                }
            }

            sendJson(exchange, 200, Map.of("message", "Nota confirmada no sistema " + systemName));
        }

        private void disagreeInvoice(HttpExchange exchange, long invoiceId) throws Exception {
            JsonObject json = GSON.fromJson(readBody(exchange), JsonObject.class);
            String systemName = json.get("system").getAsString();
            String reason = json.get("reason").getAsString();
            String details = json.has("details") ? json.get("details").getAsString() : "";

            try (Connection conn = Database.getConnection()) {
                conn.setAutoCommit(false);
                String updateStatus = """
                        UPDATE invoice_system_status st
                        JOIN systems s ON s.id = st.system_id
                        SET st.status = 'DISAGREED', st.notes = ?
                        WHERE st.invoice_id = ? AND s.name = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(updateStatus)) {
                    ps.setString(1, reason);
                    ps.setLong(2, invoiceId);
                    ps.setString(3, systemName);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        sendJson(exchange, 404, Map.of("error", "Sistema ou nota não encontrado"));
                        conn.rollback();
                        return;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO invoice_disagreements (invoice_id, system_name, reason, details) VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, invoiceId);
                    ps.setString(2, systemName);
                    ps.setString(3, reason);
                    ps.setString(4, details);
                    ps.executeUpdate();
                }

                conn.commit();
            }

            sendJson(exchange, 200, Map.of("message", "Não concordância registrada"));
        }
    }

    static Long extractInvoiceId(String path) {
        String[] parts = path.split("/");
        if (parts.length < 4) {
            return null;
        }
        if (!"api".equals(parts[1]) || !"invoices".equals(parts[2])) {
            return null;
        }
        try {
            return Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static class Database {
        static Connection getConnection() throws Exception {
            String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/invoice_manager?serverTimezone=UTC");
            String user = System.getenv().getOrDefault("DB_USER", "root");
            String pass = System.getenv().getOrDefault("DB_PASSWORD", "root");
            return DriverManager.getConnection(url, user, pass);
        }
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void writeResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream is = App.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                sendText(exchange, 404, "Resource not found");
                return;
            }
            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
