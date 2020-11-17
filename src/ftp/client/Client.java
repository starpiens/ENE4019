package ftp.client;

import ftp.DataChunkS2C;
import ftp.Response;
import ftp.ReturnCode;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class Client {

    protected class ConnectionTerminatedException extends Exception {
    }


    // Networking
    protected String host;
    protected int dataPort;
    protected Socket cmdSocket;
    protected BufferedReader stdReader;
    protected BufferedReader cmdReader;
    protected DataOutputStream ctrlOutStream;


    public void start(String host, int cmdPort, int dataPort) throws IOException {
        this.host = host;
        this.dataPort = dataPort;
        cmdSocket = new Socket(host, cmdPort);
        stdReader = new BufferedReader(new InputStreamReader(System.in));
        cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
        ctrlOutStream = new DataOutputStream(cmdSocket.getOutputStream());

        try {
            Response response = getResponse();
            while (!response.returnCode.equals(ReturnCode.SERVICE_CLOSING)) {
                String request = readStd();
                if (isGET(request)) {
                    _get(request);
                } else {
                    writeRequest(request);
                    response = getResponse();
                }
            }
        } catch (IOException | ConnectionTerminatedException e) {
            System.out.println("Connection Terminated.");
        }
    }

    protected Response getResponse() throws IOException, ConnectionTerminatedException {
        StringBuilder responseStr = new StringBuilder();
        String responseStrLine;
        while (true) {
            responseStrLine = cmdReader.readLine();
            if (responseStrLine == null) throw new ConnectionTerminatedException();
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

    protected boolean isGET(String request) {
        String[] requestSplit = request.trim().split("[ ]+");
        return (requestSplit[0].equalsIgnoreCase("get"));
    }

    protected int _get(String request) throws IOException, ConnectionTerminatedException {
        writeRequest(request);
        String[] requestSplit = request.trim().split("[ ]+");

        // Check for response.
        Response response = getResponse();
        if (response.returnCode != ReturnCode.SUCCESS) {
            return 1;
        }

        // Target info
        int targetLength = Integer.parseInt(
                response.message.trim().split("[ ]+")[1]
        );
        File srcFile = new File(requestSplit[1]);
        File dstFile = new File(srcFile.getName());

        // Setup IO streams.
        Socket dataSocket = new Socket(host, dataPort);
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(dstFile);

        // Start receiving.
        for (byte seqNo = 0; seqNo * DataChunkS2C.maxDataSize < targetLength; seqNo++) {
            byte[] bytes = dataInputStream.readNBytes(DataChunkS2C.maxChunkSize);
            System.out.print("#");
            DataChunkS2C chunk = new DataChunkS2C(bytes);
            fileOutputStream.write(chunk.data);
        }

        System.out.println("  Done.");
        dataInputStream.close();
        fileOutputStream.close();
        return 0;
    }
}
