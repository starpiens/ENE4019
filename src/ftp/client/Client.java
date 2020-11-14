package ftp.client;

import ftp.Response;
import ftp.ReturnCode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Client {

    // Networking
    protected int dataPort;
    protected Socket cmdSocket;
    BufferedReader stdReader;
    BufferedReader cmdReader;
    DataOutputStream ctrlOutStream;


    public void start(String host, int cmdPort, int dataPort) throws IOException {
        this.dataPort = dataPort;
        cmdSocket = new Socket(host, cmdPort);
        stdReader = new BufferedReader(new InputStreamReader(System.in));
        cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
        ctrlOutStream = new DataOutputStream(cmdSocket.getOutputStream());

        Response response = getResponse();
        while (!response.returnCode.equals(ReturnCode.SERVICE_CLOSING)) {
            writeRequest(readStd());
            response = getResponse();
        }
    }

    protected Response getResponse() throws IOException {
        StringBuilder responseStr = new StringBuilder();
        String responseStrLine;
        while (true) {
            responseStrLine = cmdReader.readLine();
            if (responseStrLine.isEmpty()) break;
            responseStr.append(responseStrLine).append('\n');
        }
        Response response = new Response(responseStr.toString());
        System.out.print(response.message);
        return response;
    }

    protected void writeRequest(String request) throws IOException {
        ctrlOutStream.writeBytes(request + '\n');
    }

    protected String readStd() throws IOException {
        System.out.print("> ");
        return stdReader.readLine();
    }
}
