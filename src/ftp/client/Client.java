package ftp.client;

import ftp.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.exit;


/**
 * Provides FTP client functionalities.
 */
public class Client {

    /* Networking */
    protected String host;                      // Host name, or {@code null} for the loopback address.
    protected int dataPort;                     //
    protected Socket cmdSocket;
    protected BufferedReader stdReader;
    protected BufferedReader cmdReader;
    protected DataOutputStream ctrlOutStream;

    /* Selective Repeat */
    protected final int senderTimeOut = 1;
    protected ArrayList<Integer> srDropList = new ArrayList<>();
    protected ArrayList<Integer> srTimeoutList = new ArrayList<>();
    protected ArrayList<Integer> srBiterrList = new ArrayList<>();

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
            requestHandlers.put("drop", Client.class.getDeclaredMethod("handleDROP", String[].class));
            requestHandlers.put("timeout", Client.class.getDeclaredMethod("handleTIMEOUT", String[].class));
            requestHandlers.put("biterror", Client.class.getDeclaredMethod("handleBITERR", String[].class));

        } catch (NoSuchMethodException e) {
            // This exception must not be thrown. Server goes down.
            e.printStackTrace();
            exit(1);
        }

        srDropList = new ArrayList<>(15);

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
            System.err.flush();
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
        System.out.print("Server responded: " + response.message);
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

        int returnCode = -1;
        try {
            // Call handler
            returnCode = (int) handler.invoke(this, (Object) request);

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

        return returnCode;
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
            System.out.println("No such file in client side.");
            return 1;
        }

        writeRequest(request);                      // PUT request & response
        Response response = readResponse();
        if (response.returnCode != ReturnCode.SUCCESS) {
            return 1;
        }
        writeRequest(new String[]{                  // Write metadata for sending file.
                String.valueOf(file.length()),
                " bytes"
        });


        // Preparation
        Socket dataSocket = new Socket(host, dataPort);
        BufferedReader dataInputStream = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        FileInputStream fileInputStream = new FileInputStream(file);
        DataChunkC2S[] window = new DataChunkC2S[DataChunkC2S.winSize];  // Stores data chunk.
        Timer[] timers = new Timer[DataChunkC2S.winSize];                // Each timer periodically retransmit chunks.
        int remainingChunks = (int) (file.length() + DataChunkC2S.maxDataSize - 1)
                / DataChunkC2S.maxDataSize;             // Number of chunks, not yet ACKed.
        int[] winBase = {0};                            // Index of firstly sent chunk in the window.
        int[] numBuffered = {0};                        // Number of buffered chunks in the window.
        byte[] firstSeqNo = {0};                        // First sequence number in the window.
        byte nextSeqNo = 0;                             // Next sequence number, in range of [0, maxSeqNo].
        String[] exceptionMsg = {null};                 // Set message if a thread throws IOException.
        ReentrantLock windowLock = new ReentrantLock(); // Locks window related values.
        ReentrantLock logLock = new ReentrantLock();
        Vector<Timer> timeOutTimers = new Vector<>();

        // Run ACK Listener.
        Thread ackListenerThread = new Thread(new ACKListener(
                dataInputStream, exceptionMsg, winBase, numBuffered, firstSeqNo, windowLock, logLock, timers
        ));
        ackListenerThread.start();

        try {
            // Send file.
            while (remainingChunks > 0) {
                // Create data chunks, and fill window.
                while (numBuffered[0] < DataChunkC2S.winSize && numBuffered[0] < remainingChunks) {
                    int idx = (winBase[0] + numBuffered[0]) % DataChunkC2S.winSize;
                    byte[] data = fileInputStream.readNBytes(DataChunkC2S.maxDataSize);
                    window[idx] = new DataChunkC2S(nextSeqNo, data);    // Create data chunk.
                    timers[idx] = new Timer();
                    timers[idx].scheduleAtFixedRate(                    // Setup timer for retransmission, and start it.
                            new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        synchronized (dataOutputStream) {
                                            logLock.lock();
                                            window[idx].writeBytes(dataOutputStream);
                                            System.out.println("Timeout, resent: " + window[idx].getSeqNo());
                                        }
                                    } catch (IOException e) {
                                        exceptionMsg[0] = e.getMessage();
                                    } finally {
                                        logLock.unlock();
                                    }
                                }
                            },
                            senderTimeOut * 1000,
                            senderTimeOut * 1000
                    );
                    windowLock.lock();
                    numBuffered[0]++;
                    windowLock.unlock();

                    if (srDropList.contains((int) nextSeqNo)) {
                        ;   // Don't send it.

                    } else if (srBiterrList.contains((int) nextSeqNo)) {
                        // Make bit error, and send it.
                        window[idx].setErr(true);
                        synchronized (dataOutputStream) {
                            try {
                                logLock.lock();
                                window[idx].writeBytes(dataOutputStream);
                                System.out.println("Sent:  " + window[idx].getSeqNo() + " --> Server");
                            } finally {
                                logLock.unlock();
                            }
                        }
                        window[idx].setErr(false);

                    } else if (srTimeoutList.contains((int) nextSeqNo)) {
                        // Add timer for sending.
                        timeOutTimers.add(new Timer());
                        timeOutTimers.lastElement().schedule(
                                new TimerTask() {
                                    final DataChunkC2S data = window[idx];

                                    @Override
                                    public void run() {
                                        try {
                                            synchronized (dataOutputStream) {
                                                logLock.lock();
                                                data.writeBytes(dataOutputStream);
                                                System.out.println("Lately sent: " + data.getSeqNo());
                                            }
                                        } catch (IOException e) {
                                            exceptionMsg[0] = e.getMessage();
                                        } finally {
                                            logLock.unlock();
                                        }
                                    }
                                },
                                2 * senderTimeOut * 1000
                        );

                    } else {
                        try {
                            synchronized (dataOutputStream) {
                                logLock.lock();
                                window[idx].writeBytes(dataOutputStream);
                                System.out.println("Sent:  " + window[idx].getSeqNo() + " --> Server");
                            }
                        } finally {
                            logLock.unlock();
                        }
                    }

                    nextSeqNo = (byte) ((nextSeqNo + 1) % DataChunkC2S.numSeqNo);
                }

                if (exceptionMsg[0] != null) {                // Check for exception thrown in thread.
                    throw new IOException(exceptionMsg[0]);
                }

                // Slide window.
                timers[winBase[0]] = timers[winBase[0]];        // Reload, 'cause it bay be modified in ACKListener.
                while (timers[winBase[0]] == null && numBuffered[0] > 0) {
                    // If the first sequence in window is ACKed, slide window.
                    try {
                        windowLock.lock();
                        firstSeqNo[0] = (byte) ((firstSeqNo[0] + 1) % DataChunkC2S.numSeqNo);
                        winBase[0] = (winBase[0] + 1) % DataChunkC2S.winSize;
                        numBuffered[0]--;
                        remainingChunks--;
                    } finally {
                        windowLock.unlock();
                    }
                }
            }
            System.out.println("  Done.");

        } finally {
            ackListenerThread.interrupt();
            fileInputStream.close();
            dataOutputStream.close();
            dataInputStream.close();
            dataSocket.close();
            srDropList.clear();
            srBiterrList.clear();
            srTimeoutList.clear();
        }

        return 0;
    }

    protected int handleDROP(String[] request) {
        String[] reqSplit = request[1].split(",");

        srBiterrList.clear();
        srTimeoutList.clear();
        try {
            for (String s : reqSplit) {
                s = s.substring(1);
                int chunkNo = Integer.parseInt(s);
                srDropList.add(chunkNo);
            }

        } catch (NumberFormatException e) {
            System.out.println("Failed to parse.");
            return 1;
        }

        return 0;
    }

    protected int handleTIMEOUT(String[] request) {
        String[] reqSplit = request[1].split(",");

        srDropList.clear();
        srBiterrList.clear();
        try {
            for (String s : reqSplit) {
                s = s.substring(1);
                int chunkNo = Integer.parseInt(s);
                srTimeoutList.add(chunkNo);
            }

        } catch (NumberFormatException e) {
            System.out.println("Failed to parse.");
            return 1;
        }

        return 0;
    }

    protected int handleBITERR(String[] request) {
        String[] reqSplit = request[1].split(",");

        srDropList.clear();
        srTimeoutList.clear();
        try {
            for (String s : reqSplit) {
                s = s.substring(1);
                int chunkNo = Integer.parseInt(s);
                srBiterrList.add(chunkNo);
            }

        } catch (NumberFormatException e) {
            System.out.println("Failed to parse.");
            return 1;
        }

        return 0;
    }
}
