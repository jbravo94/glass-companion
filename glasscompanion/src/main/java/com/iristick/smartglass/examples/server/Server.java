package com.iristick.smartglass.examples.server;

import com.iristick.smartglass.examples.camera.CameraFragment;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Server implements Runnable {

    private CameraFragment camera0;
    private CameraFragment camera1;

    public Server(CameraFragment camera0, CameraFragment camera1) {
        this.camera0 = camera0;
        this.camera1 = camera1;
    }

    @Override
    public void run() {
        HttpServer server = null;

        try {
            server = HttpServer.create(new InetSocketAddress(8080),0);
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

    class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException
        {
            byte[] response = "<!DOCTYPE html><html><body><a href=\"./camera0\">Front Camera</a><br/><a href=\"./camera1\">Side Camera</a></body></html>".getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class CameraHandler implements HttpHandler {
        private int cameraIndex;

        public CameraHandler(int cameraIndex) {
            this.cameraIndex = cameraIndex;
        }

        @Override
        public void handle(HttpExchange t) throws IOException
        {
            byte[] response = ("<!DOCTYPE html><html><body><img src=\"./stream" + cameraIndex + "\"></body></html>").getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class StreamHandler implements HttpHandler {

        private CameraFragment cameraFragment;

        StreamHandler(CameraFragment cameraFragment) {
            this.cameraFragment = cameraFragment;
        }

        private static final String NL = "\r\n";
        private static final String BOUNDARY = "--boundary";
        private static final String HEAD = BOUNDARY + NL +
                "Content-Type: image/jpeg" + NL +
                "Content-Length: ";

        @Override
        public void handle(HttpExchange t) throws IOException {

            Headers h = t.getResponseHeaders();
            h.set("Cache-Control", "no-cache, private");
            h.set("Content-Type", "multipart/x-mixed-replace;boundary=" + BOUNDARY);
            t.sendResponseHeaders(200, 0);
            OutputStream os = t.getResponseBody();

            while (true) {

                byte[] img = cameraFragment.getLastImage();

                if (img == null) {
                    continue;
                }

                os.write((HEAD + img.length + NL + NL).getBytes());

                os.write(img);

                os.write((NL).getBytes());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //os.close();
        }
    }
}
