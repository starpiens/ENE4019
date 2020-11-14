package ftp.server;

import ftp.Response;
import ftp.ReturnCode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    static class ClientManager {

        // Networking
        protected Socket cmdSocket;
        protected BufferedReader cmdReader;
        protected DataOutputStream cmdOutStream;
        protected int dataPort;

        // Client status
        protected String pwd;


        public void start(Socket cmdSocket, int dataPort) throws IOException {
            this.cmdSocket = cmdSocket;
            this.dataPort = dataPort;
            cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
            cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());
            writeResponse(new Response(ReturnCode.SERVICE_READY, "Hello\n"));

            while (true) {
                String request = getRequest();
                if (request == null) continue;
                String cmd = request.substring(0, request.indexOf(' '));
                String arg = request.substring(request.indexOf(' ') + 1);
                writeResponse(runCmd(cmd, arg));
                if (cmd.equalsIgnoreCase("quit")) break;
            }

            cmdSocket.close();
        }

        protected String getRequest() throws IOException {
            String str = cmdReader.readLine();
            System.out.println("Request: " + str);
            return str;
        }

        protected void writeResponse(Response response) throws IOException {
            String responseStr = response.toString();
            cmdOutStream.writeBytes(responseStr);
            // Print only first line
            System.out.println("Response: " + responseStr.substring(0, responseStr.indexOf('\n')));
        }

        protected Response runCmd(String cmd, String arg) {
            cmd = cmd.toLowerCase();
            if (cmd.equals("list")) {
                return _list(arg);
            } else if (cmd.equals("get")) {
                return _get(arg);
            } else {
                return new Response(ReturnCode.UNRECOGNIZED, "Unknown command\n");
            }
        }

        protected Response _list(String arg) {
            return new Response(ReturnCode.SUCCESS, "OK\n");
        }

        protected Response _get(String arg) {
            return new Response(ReturnCode.SUCCESS, "OK\n");
        }

    }


    public void start(int cmdPort, int dataPort) throws IOException {
        ServerSocket serverSocket = new ServerSocket(cmdPort);
        while (true) {
            Socket cmdSocket = serverSocket.accept();
            ClientManager manager = new ClientManager();
            manager.start(cmdSocket, dataPort);
        }
    }

}
