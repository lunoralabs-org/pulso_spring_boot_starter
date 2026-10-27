package dev.lunoralabs.pulso.starter;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A stand-in for the Pulso server that records every request it gets. */
class FakePulso implements AutoCloseable {

    record Request(String method, String path, String query, String apiKey, String contentType, String body) {
    }

    private final HttpServer server;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

    FakePulso() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders().getFirst(PulsoClient.API_KEY_HEADER),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The next request, waiting up to five seconds for it. */
    Request next() throws InterruptedException {
        Request request = requests.poll(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Pulso got no request");
        }
        return request;
    }

    /** Whether any request arrives within the given time. */
    boolean receivesWithin(long millis) throws InterruptedException {
        return requests.poll(millis, TimeUnit.MILLISECONDS) != null;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
