package ftp.client;

import ftp.*;

import javax.xml.crypto.Data;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.*;

import static java.lang.System.exit;


/**
 * Provides FTP client functionalities.
 */
public class Client {

    /* Networking */
    protected String host;
    protected int dataPort;
    protected Socket cmdSocket;
    protected BufferedReader stdReader;
    protected BufferedReader cmdReader;
    protected DataOutputStream ctrlOutStream;

    /* SR Parameters */
    protected final byte maxSeqNo = 15;
    protected final int winSize = 5;
    protected final int senderTimeOut = 1;

    // Maps request string to request handler.
    protected final Map<String, Method> requestHandlers;


    /**
     * Create new client.
     */
    public Client() {
        requestHandlers = new HashMap<>();
        try {
            requestHandlers.put("get", Client.class.getDeclaredMethod("handleGET", String[].class));
            requestHandlers.put("put", Client.class.getDeclaredMethod("handlePUT", String[].class));

        } catch (NoSuchMethodException e) {
            // This exception must not be thrown. Server goes down.
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Start connection with server.
     *
     * @param host     Host name, or {@code null} for the loopback address.
     * @param cmdPort  Port number of command channel.
     * @param dataPort Port number of data channel.
     * @throws IOException If IO exception occurred while initiating connection with server.
     */
    public void start(String host, int cmdPort, int dataPort) throws IOException {
        this.host = host;
        this.dataPort = dataPort;
        // Open IO stream.
        cmdSocket = new Socket(host, cmdPort);
        stdReader = new BufferedReader(new InputStreamReader(System.in));
        cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
        ctrlOutStream = new DataOutputStream(cmdSocket.getOutputStream());

        try {
            System.err.println("Connection established: " + cmdSocket.getInetAddress());
            // Hello!
            readResponse();

            int handleRequestReturnCode;
            do {
                // Get request, and process it.
                String[] request = readRequest();
                handleRequestReturnCode = handleRequest(request);
            } while (handleRequestReturnCode != -1);

            // Connection closed normally.
            System.err.println("Connection successfully closed: " + cmdSocket.getInetAddress());

        } catch (IOException e) {
            // Connection accidentally closed because of IOException.
            System.err.println("Connection accidentally closed: " + cmdSocket.getInetAddress());
            System.err.println("Details: " + e.getMessage());

        } finally {
            // Cleanup connection.
            try {
                handleRequest(new String[]{"quit"});
                stdReader.close();
                cmdReader.close();
                ctrlOutStream.close();
                cmdSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Get response from server. Reads serialized string from {@code cmdReader},
     * and deserialize as {@code Response} instance.
     *
     * @return Response from server.
     * @throws IOException If failed reading response from server.
     */
    protected Response readResponse() throws IOException {
        StringBuilder responseStr = new StringBuilder();
        String responseStrLine;
        while (true) {
            responseStrLine = cmdReader.readLine();
            if (responseStrLine == null) throw new IOException("It seems server is down");
            if (responseStrLine.isEmpty()) break;
            responseStr.append(responseStrLine).append('\n');
        }
        Response response = new Response(responseStr.toString());
        System.out.print(response.message);
        return response;
    }

    /**
     * Write a single-line request to the server. Guarantees that the request message
     * to the server is always single-line.
     *
     * @param request A single line request message. If it has multiple lines of request,
     *                all lines are not being sent but first line.
     * @throws IOException If failed to write request to server.
     */
    protected void writeRequest(String[] request) throws IOException {
        for (String req : request) {
            int i = req.indexOf('\n');
            if (i != -1) {
                req = req.substring(0, i);
                ctrlOutStream.writeBytes(req);
                break;
            } else {
                ctrlOutStream.writeBytes(req + ' ');
            }
        }
        ctrlOutStream.write('\n');
    }

    /**
     * Read request from standard input, and split it by space as delimiter.
     * Any leading or trailing spaces are removed.
     *
     * @return Array of strings.
     * Name of a command is stored at index 0, and other arguments follow.
     * @throws IOException If failed reading request from standard input.
     */
    protected String[] readRequest() throws IOException {
        System.out.print("> ");
        String str = stdReader.readLine();
        if (str == null) throw new IOException("Failed reading request from stdin");
        return str.trim().split("[ ]+");
    }

    /**
     * Handles a request from user. That includes writing request to server, and reading response
     * from client. For more complex requests, appropriate handler in {@code requestHandlers}
     * is being handed over for handling.
     *
     * @param request Request to be handled.
     * @return 0 if request is successfully handled, and -1 if client wants to quit.
     * @throws IOException If an IO exception occurred while writing the request or reading the response.
     */
    protected int handleRequest(String[] request) throws IOException {
        // Find method
        Method handler = requestHandlers.get(request[0].toLowerCase());

        if (handler == null) {
            // No specific handler for such command. Just send it to server.
            writeRequest(request);
            readResponse();
            return 0;
        }

        try {
            // Call handler
            handler.invoke(this, (Object) request);

        } catch (IllegalAccessException e) {        // This exception must not be thrown. Client goes down.
            e.printStackTrace();
            exit(1);

        } catch (InvocationTargetException e) {     // Callee has thrown an exception.
            if (e.getCause().getClass().equals(IOException.class)) {    // Is it because of IOException?
                throw (IOException) e.getCause();
            } else {                                                    // .. or something else happened?
                e.printStackTrace();                                    // If so, client goes down.
                exit(1);
            }
        }

        return 0;
    }

    /**
     * Handler for {@code GET} command. Receive requested file from server via data channel,
     * and save it to the path where client is running at. If name of file collides, TODO !!!!
     *
     * @param request Name of file(s) starting at index 1.
     *                Supports paths relative to current path on server.
     * @return 0 in case of success, non-zero value in case of failure.
     * @throws IOException If an IO exception occurred.
     */
    protected int handleGET(String[] request) throws IOException {
        writeRequest(request);

        // Check for response.
        Response response = readResponse();
        if (response.returnCode != ReturnCode.SUCCESS) {
            return 1;
        }

        // Target info
        int targetLength = Integer.parseInt(
                response.message.trim().split("[ ]+")[1]
        );
        File srcFile = new File(request[1]);
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
        dataSocket.close();
        dataInputStream.close();
        fileOutputStream.close();
        return 0;
    }

    /**
     * Handler for {@code PUT} command. Send requested file to server via data channel,
     * and save it to the current path on the server.
     *
     * @param request Name of file(s) starting at index 1.
     *                Supports paths relative to where client is running at.
     * @return 0 in case of success, non-zero value in case of failure.
     * @throws IOException If an IO exception occurred.
     */
    protected int handlePUT(String[] request) throws IOException {
        File file = new File(request[1]);
        if (!file.isFile()) {
            return 1;
        }

        writeRequest(request);                      // PUT request & response
        Response response = readResponse();
        if (response.returnCode != ReturnCode.SUCCESS) {
            return 1;
        }
        writeRequest(new String[]{                  // Write metadata for sending file.
                String.valueOf(file.length())
        });

        // Preparation
        Socket dataSocket = new Socket(host, dataPort);
        BufferedReader dataInputStream = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        FileInputStream fileInputStream = new FileInputStream(file);
        DataChunkC2S[] window = new DataChunkC2S[winSize];  // Stores data chunk.
        Timer[] timers = new Timer[winSize];                // Each timer periodically sends data chunk,
        for (int i = 0; i < timers.length; i++)             // stored in the window until being disabled.
            timers[i] = new Timer();
        int remainingChunks = (int) (file.length() + DataChunkC2S.maxChunkSize - 1)
                / DataChunkC2S.maxChunkSize;                // Number of chunks, not yet ACKed.
        int winBase = 0;                                    // Index of firstly sent chunk in the window.
        int numBuffered = 0;                                // Number of buffered chunks in the window.
        byte nextSeqNo = 0;                                 // Next sequence number, in range of [0, maxSeqNo].
        ThreadIOException ioEX = new ThreadIOException();   // Set message if a thread throws IOException.

        // Send file.
        while (remainingChunks > 0) {
            // Make chunk, and fill window.
            while (numBuffered < winSize && numBuffered < remainingChunks) {
                int idx = (winBase + numBuffered) % winSize;
                byte[] data = fileInputStream.readNBytes(DataChunkC2S.maxChunkSize);
                window[idx] = new DataChunkC2S(nextSeqNo, data);    // Create data chunk.
                timers[idx].scheduleAtFixedRate(                    // Setup timer and start it.
                        new TimerTask() {
                            @Override
                            public void run() {
                                try {
                                    window[idx].writeBytes(dataOutputStream);
                                    ioEX.message = "TEST";                  // TODO: Remove it
                                } catch (IOException e) {
                                    ioEX.message = e.getMessage();
                                }
                            }
                        },
                        0,
                        senderTimeOut * 1000
                );
                numBuffered++;
                nextSeqNo = (byte) ((nextSeqNo + 1) % (maxSeqNo + 1));
            }

            if (ioEX.message != null) {                // Check for exception has thrown in thread.
                throw new IOException(ioEX.message);
            }

            // Get ACK. TODO: Make separate thread to check ACK message.
            int ACKed = Integer.parseInt(dataInputStream.readLine());
            int firstSeqNo = (nextSeqNo - numBuffered + maxSeqNo + 1) % (maxSeqNo + 1);
            int logicalNextSeqNo = nextSeqNo + ((nextSeqNo < firstSeqNo) ? (maxSeqNo + 1) : 0);
            int logicalACKed = ACKed + ((ACKed < firstSeqNo) ? (maxSeqNo + 1) : 0);
            if (firstSeqNo <= logicalACKed && logicalACKed < logicalNextSeqNo) {
                // ACKed sequence number is in range.
                int idx = (winBase + logicalACKed - firstSeqNo) % winSize;
                window[idx] = null;
                timers[idx].cancel();
                while (window[winBase] == null && numBuffered > 0) {
                    // Slide window.
                    winBase = (winBase + 1) % winSize;
                    numBuffered--;
                    remainingChunks--;
                }
            }

        }

        System.out.println("  Done.");
        dataSocket.close();
        dataOutputStream.close();
        return 0;
    }

}
