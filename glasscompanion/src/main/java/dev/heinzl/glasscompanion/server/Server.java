package dev.heinzl.glasscompanion.server;

import dev.heinzl.glasscompanion.camera.CameraFragment;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Server implements Runnable {

    private final CameraFragment camera0;
    private final CameraFragment camera1;

    private boolean serverWillBeStopped = false;

    public static final int SERVER_PORT = 8080;

    private HttpServer server = null;

    public Server(CameraFragment camera0, CameraFragment camera1) {
        this.camera0 = camera0;
        this.camera1 = camera1;
    }

    @Override
    public void run() {

        try {
            server = HttpServer.create(new InetSocketAddress(SERVER_PORT),0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        server.createContext("/", new RootHandler());
        server.createContext("/camera0", new CameraHandler(0));
        server.createContext("/camera1", new CameraHandler(1));
        server.createContext("/stream0", new StreamHandler(camera0));
        server.createContext("/stream1", new StreamHandler(camera1));
        server.setExecutor(null);
        server.start();
    }

    public void stopServer() {
        if (server != null) {
            serverWillBeStopped = true;

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            server.stop(0);
        }
    }

    class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException
        {
            byte[] response = "<!DOCTYPE html><html><body><div style=\"text-align: center;\"><h1>Glass Companion</h1><a href=\"./camera0\"><h2>Front Camera</h2></a><a href=\"./camera1\"><h2>Side Camera</h2></a></div></body></html>".getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class CameraHandler implements HttpHandler {
        private final int cameraIndex;

        public CameraHandler(int cameraIndex) {
            this.cameraIndex = cameraIndex;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException
        {
            byte[] response = ("<!DOCTYPE html><html><body><div style=\"text-align: center;\"><img src=\"./stream" + cameraIndex + "\"></div></body></html>").getBytes();
            httpExchange.sendResponseHeaders(200, response.length);
            OutputStream outputStream = httpExchange.getResponseBody();
            outputStream.write(response);
            outputStream.close();
        }
    }

    class StreamHandler implements HttpHandler {

        private final CameraFragment cameraFragment;

        StreamHandler(CameraFragment cameraFragment) {
            this.cameraFragment = cameraFragment;
        }

        private static final String NL = "\r\n";
        private static final String BOUNDARY = "--boundary";
        private static final String HEAD = BOUNDARY + NL +
                "Content-Type: image/jpeg" + NL +
                "Content-Length: ";

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {

            Headers headers = httpExchange.getResponseHeaders();
            headers.set("Cache-Control", "no-cache, private");
            headers.set("Content-Type", "multipart/x-mixed-replace;boundary=" + BOUNDARY);
            httpExchange.sendResponseHeaders(200, 0);

            try (OutputStream outputStream = httpExchange.getResponseBody()) {

                while (!serverWillBeStopped && !Thread.interrupted()) {

                    byte[] image = cameraFragment.getLastImage();

                    if (image == null) {
                        continue;
                    }

                    outputStream.write((HEAD + image.length + NL + NL).getBytes());

                    outputStream.write(image);

                    outputStream.write((NL).getBytes());

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }
}
