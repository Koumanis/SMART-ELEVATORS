package gr.aueb.dmst.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import gr.aueb.dmst.elevator.ElevatorModel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class RestServer {
    private final int port;
    private final ObjectMapper mapper;
    private final List<ElevatorModel> elevators;

    public RestServer(int port) {
        this.port = port;
        this.mapper = new ObjectMapper();
        this.elevators = new ArrayList<>();
        
        // Αρχικοποίηση με ένα δοκιμαστικό ασανσέρ (E1)
        this.elevators.add(new ElevatorModel("E1"));
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoint: GET /api/elevators
        server.createContext("/api/elevators", new ElevatorsHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("REST Server running on http://localhost:" + port);
        System.out.println("Web UI Dashboard: http://localhost:8080/");
    }

    private class ElevatorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Μετατροπή της λίστας των ασανσέρ σε JSON κείμενο
                String jsonResponse = mapper.writeValueAsString(elevators);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                byte[] responseBytes = jsonResponse.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            }
        }
    }

    public static void main(String[] args) throws IOException {
        RestServer server = new RestServer(8080);
        server.start();
    }
}