import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import FTP.ReturnCode;

public class Server {

    protected class ClientStatus {
        String pwd;
    }

    protected class Response {
        ReturnCode returnCode;
        String phrase;

        Response(ReturnCode returnCode, String phrase) {
            this.returnCode = returnCode;
            this.phrase = phrase;
        }

        public String toString() {
            return Integer.toString(returnCode.getCodeNum()) + " " + phrase;
        }
    }

    protected Response _list(String arg) {
        return new Response(Return)
    }

    protected String getRequest(BufferedReader reader) throws IOException {
        String str = reader.readLine();
        System.out.println("Request: " + str);
        return str;
    }

    protected void writeResponse(DataOutputStream stream, Response response) throws IOException {
        stream.writeBytes(response.toString() + '\n');
        System.out.println(response.toString());
    }

    protected void serveClient(Socket cmdSocket, int dataPort) throws IOException {
        // Setup IO stream and test.
        BufferedReader cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
        DataOutputStream cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());

        writeResponse(cmdOutStream, new Response(ReturnCode.SERVICE_READY, "Hello"));

        // Serve client.
        while (true) {
            String request = getRequest(cmdReader);
            String cmd = request.substring(0, request.indexOf(' ')).toLowerCase();
            String arg = request.substring(request.indexOf(' ') + 1);
            Response response;

            if (cmd.equals("list")) {
                response = _list(arg);
            } else if (cmd.equals("get")) {
                ;
            } else if (cmd.equals("quit")) {
                break;
            } else {
                response = new Response(ReturnCode.SERVICE_CLOSING, "Bye");
            }
            writeResponse(cmdOutStream, response);
        }

        writeResponse(cmdOutStream, new Response(ReturnCode.SERVICE_CLOSING, "Bye"));
    }

    public void start(int cmdPort, int dataPort) throws IOException {
        ServerSocket serverSocket = new ServerSocket(cmdPort);
        while (true) {
            Socket ctrlSocket = serverSocket.accept();
            serveClient(ctrlSocket, dataPort);
        }
    }

    public static void main(String[] args) throws IOException {
        int cmdPort = 2020;
        int dataPort = 2021;
        try {
            cmdPort = Integer.parseInt(args[0]);
            dataPort = Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        Server server = new Server();
        server.start(cmdPort, dataPort);
    }
}
