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
    private final Dispatcher dispatcher;

    public RestServer(int port) {
        this.port = port;
        this.mapper = new ObjectMapper();
        this.elevators = new ArrayList<>();
        
        this.elevators.add(new ElevatorModel("E1", 0));
        this.elevators.add(new ElevatorModel("E2", 60));
        this.elevators.add(new ElevatorModel("E3", 130));

        this.dispatcher = new Dispatcher(this.elevators);
    }


    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Endpoint: GET /api/elevators
        server.createContext("/api/elevators", new ElevatorsHandler());
        server.createContext("/api/calls", new CallsHandler());

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

    private class CallsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Pre-flight check
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Ανάγνωση του JSON body από το UI
                byte[] requestBytes = exchange.getRequestBody().readAllBytes();

                CallRequest call = mapper.readValue(requestBytes, CallRequest.class);

                // Ανάθεση μέσω της νέας κλάσης Dispatcher
                ElevatorModel assigned = dispatcher.assignElevator(call);

                System.out.printf("🎯 [Dispatcher] Ανάθεση κλήσης [%d -> %d] στον ανελκυστήρα %s\n",
                call.getSourceFloor(), call.getDestinationFloor(), assigned.getId());

                assigned.processTrip(call.getSourceFloor(), call.getDestinationFloor());

                // Επιστροφή επιβεβαίωσης στο UI
                String responseJson = "{\"status\":\"ACCEPTED\",\"message\":\"Call registered successfully\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = responseJson.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        RestServer server = new RestServer(8080);
        server.start();
    }
}