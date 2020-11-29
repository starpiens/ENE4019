package ftp.server;

import ftp.DataChunkC2S;
import ftp.DataChunkS2C;
import ftp.Response;
import ftp.ReturnCode;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.exit;


/**
 * Provides FTP server functionalities,
 * such as opening up new connection and executing some file-related commands from remote clients.
 * Indeed, it is not yet implemented via multi-thread, so it cannot serve multiple clients in parallel.
 */
public class Server {

    /**
     * A class which serves single client.
     * When new client comes, Server creates multiple instances of ClientManager.
     */
    protected class ClientHandler {

        /* Networking */
        protected Socket cmdSocket;
        protected BufferedReader cmdReader;
        protected DataOutputStream cmdOutStream;
        protected ServerSocket serverDataSocket;

        /* Client status */
        protected File pwd = defaultPath;

        /**
         * Start serving a client.
         *
         * @param   cmdSocket
         *          Opened socket for command channel.
         * @param   serverDataSocket
         *          Opened server socket for data channel.
         *
         * @throws  IOException
         *          If IO exception occurred while initiating connection with client.
         */
        public void start(Socket cmdSocket, ServerSocket serverDataSocket) throws IOException {
            this.cmdSocket = cmdSocket;
            this.serverDataSocket = serverDataSocket;
            // Open IO stream.
            cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
            cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());

            try {
                System.err.println("Connection established: " + cmdSocket.getInetAddress());
                // Say hello!
                writeResponse(new Response(ReturnCode.SERVICE_READY, "Hello\n"));

                int handleRequestReturnCode;
                do {
                    // Get request, and process it.
                    String[] request = getRequest();
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
                    writeResponse(new Response(ReturnCode.SERVICE_CLOSING, "Closing service"));
                    cmdOutStream.close();
                    cmdReader.close();
                    cmdSocket.close();
                } catch (IOException ignored) {
                }
            }
        }

        /**
         * Get request from a client, and split it by space as delimiter. Any leading or trailing
         * spaces are removed. Also prints request to standard output.
         *
         * @return  Array of strings.
         *          Name of a command is stored at index 0, and other arguments follow.
         *
         * @throws  IOException
         *          If failed reading request from client.
         */
        protected String[] getRequest() throws IOException {
            String str = cmdReader.readLine();
            if (str == null) throw new IOException("Client seems down");
            System.out.println("Request: " + str);
            return str.trim().split("[ ]+");
        }

        /**
         * Serialize response, and write it to a client.
         * Also prints first line of response to standard output.
         *
         * @param   response
         *          Response to write.
         *
         * @throws  IOException
         *          If IO exception occurred.
         */
        protected void writeResponse(Response response) throws IOException {
            String responseStr = response.toString();
            cmdOutStream.writeBytes(responseStr);
            System.out.println("Response: " + responseStr.substring(0, responseStr.indexOf('\n')));
        }

        /**
         * Handles a request from client, and write response to the client.
         * Instead of handling request directly here, it finds an appropriate handler in
         * {@code requestHandlers} and hands over all of the responsibility to it.
         *
         * @param   request
         *          Request to be handled.
         *
         * @return  0 if request is successfully handled, and -1 if client wants to quit.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int handleRequest(String[] request) throws IOException {
            // Quit request?
            if (request[0].equalsIgnoreCase("quit")) {
                return -1;
            }

            // Find method
            Method handler = requestHandlers.get(request[0].toLowerCase());

            if (handler == null) {
                // No handler for such command.
                writeResponse(new Response(ReturnCode.UNRECOGNIZED, "Unknown command\n"));
                return 0;
            }

            try {
                // Call handler
                handler.invoke(this, (Object) request);

            } catch (IllegalAccessException e) {    // This exception must not be thrown. Server goes down.
                e.printStackTrace();
                exit(1);

            } catch (InvocationTargetException e) {     // Callee has thrown an exception.
                if (e.getCause().getClass().equals(IOException.class)) {    // Is it because of IOException?
                    throw (IOException) e.getCause();
                } else {                                                    // .. or something else happened?
                    e.printStackTrace();                                    // If so, server goes down.
                    exit(1);
                }
            }

            return 0;
        }

        /**
         * Handler for {@code LIST} command.
         * Get list of files in requested directory and response it to the requester.
         * Supports relative path on {@code pwd}.
         *
         * @param   request
         *          Name of the directory to list in {@code request[1]}.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int handleLIST(String[] request) throws IOException {
            // Check arguments.
            if (request.length != 2) {
                writeResponse(new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Single argument required\n"
                ));
                return 1;
            }

            // Resolve target path.
            File targetPath = pwd.toPath().resolve(request[1]).toFile();

            if (!targetPath.exists()) {
                // Target doesn't exist.
                writeResponse(new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Directory doesn't exist"
                ));
                return 1;

            } else if (!targetPath.isDirectory()) {
                // Target is not a directory.
                writeResponse(new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Not a directory"
                ));
                return 1;
            }

            // Get file list.
            File[] fileList = targetPath.listFiles();

            if (fileList != null) {
                // In case of success
                Response response = new Response(ReturnCode.SUCCESS);
                response.message = "Comprising " + fileList.length +
                        (fileList.length < 2 ? " entry" : " entries") + "\n";
                StringBuilder messageBuilder = new StringBuilder();
                for (File file : fileList) {
                    messageBuilder.append(file.getName()).append(", ");
                    if (file.isDirectory())
                        messageBuilder.append("-\n");
                    else
                        messageBuilder.append(file.length()).append("\n");
                }
                response.message += messageBuilder;
                writeResponse(response);
                return 0;

            } else {
                // In case of failure
                writeResponse(new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Unable to access. Check if you have enough permission."
                ));
                return 1;
            }
        }

        /**
         * Handler for {@code GET} command.
         * Send requested file to client via data channel.
         * Supports paths relative to {@code pwd}.
         *
         * @param   request
         *          Name of file(s) starting at index 1.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred.
         */
        protected int handleGET(String[] request) throws IOException {
            // Check arguments.
            if (request.length != 2) {
                writeResponse(new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Single argument required\n"
                ));
                return 1;
            }

            // Resolve target path.
            File targetFile = pwd.toPath().resolve(request[1]).toFile();

            // Check availability of the file.
            if (!targetFile.exists()) {
                // Target doesn't exist.
                writeResponse(new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "File doesn't exist"
                ));
                return 1;

            } else if (!targetFile.isFile()) {
                // Target is not a file.
                writeResponse(new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Not a file"
                ));
                return 1;
            }

            // Success.
            writeResponse(new Response(
                    ReturnCode.SUCCESS,
                    "Containing " + targetFile.length() + " bytes in total"
            ));

            // Setup IO streams.
            Socket dataSocket = serverDataSocket.accept();
            DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
            FileInputStream fileInputStream = new FileInputStream(targetFile);

            // Start sending.
            for (byte seqNo = 0; ; seqNo++) {
                byte[] data = fileInputStream.readNBytes(DataChunkS2C.maxDataSize);
                if (data.length == 0) break;
                DataChunkS2C chunk = new DataChunkS2C(seqNo, data);
                chunk.writeBytes(dataOutputStream);
                System.out.print("#");
            }

            System.out.println("  Done.");
            dataSocket.close();
            dataOutputStream.close();
            fileInputStream.close();
            return 0;
        }

        /**
         * Handler for {@code PUT} command.
         * Receive requested file from client via data channel.
         * Supports relative path on {@code pwd}.
         *
         * @param   request
         *          Name of file(s) starting at index 1. If the name collides, refuse the request.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int handlePUT(String[] request) throws IOException {
            if (request.length != 2) {                  // Check arguments.
                writeResponse(new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Single argument required\n"
                ));
                return 1;
            }

            // Resolve target path and check availability.
            File file = pwd.toPath().resolve(request[1]).toFile();
            if (file.exists()) {
                // If target file name already exists, deny.
                writeResponse(new Response(
                        ReturnCode.NAME_NOT_ALLOWED,
                        "File or directory already exists"
                ));
                return 1;
            }

            // Success, get target length.
            writeResponse(new Response(
                    ReturnCode.SUCCESS,
                    "Ready to receive"
            ));
            int targetLength = Integer.parseInt(getRequest()[0]);
            int remainingChunks = (targetLength + DataChunkC2S.maxDataSize - 1)
                    / DataChunkC2S.maxDataSize;                 // Total number of chunks to be received.

            // Preparation
            Socket dataSocket = serverDataSocket.accept();
            DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
            DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            DataChunkC2S[] window = new DataChunkC2S[DataChunkC2S.winSize];  // Stores data chunk.
            int winBase = 0;                                    // Index of firstly sent chunk in the window.
            int numBuffered = 0;                                // Number of buffered chunks in the window.
            int firstSeqNo = 0;                                 // First sequence number in the window.

            // Receive file.
            while (remainingChunks > 0) {
                // Read header, and check sequence number.
                byte[] header = dataInputStream.readNBytes(DataChunkC2S.headerSize);
                DataChunkC2S chunk = new DataChunkC2S(header);
                int seqNo = chunk.getSeqNo();
                int logicalSeqNo = seqNo - firstSeqNo +     // Offset relative to firstSeqNo. -winSize <= logicalSeqNo.
                        ((seqNo - firstSeqNo < -DataChunkC2S.winSize) ? (DataChunkC2S.maxSeqNo + 1) : 0);

                if (logicalSeqNo < 0) {
                    // Sender resent it possibly because of dropped ACK. Just ACK back.
                    dataOutputStream.writeBytes(Integer.toString(seqNo) + '\n');
                } else if (logicalSeqNo < DataChunkC2S.winSize) {
                    // Sequence number is in range. Buffer it, and ACK.
                    chunk.setData(
                            dataInputStream.readNBytes(chunk.getSize())
                    );
                    if (chunk.isError()) continue;      // If there is bit error, do nothing.
                    int idx = (winBase + logicalSeqNo) % DataChunkC2S.winSize;
                    window[idx] = chunk;
                    numBuffered++;
                    dataOutputStream.writeBytes(Integer.toString(seqNo) + '\n');
                    while (window[winBase] != null && numBuffered > 0) {
                        // If the first sequence in window came, slide window.
                        System.out.print(firstSeqNo + " ");
                        fileOutputStream.write(window[winBase].data);
                        window[winBase] = null;
                        firstSeqNo = (byte) ((firstSeqNo + 1) % (DataChunkC2S.maxSeqNo + 1));
                        winBase = (winBase + 1) % DataChunkC2S.winSize;
                        numBuffered--;
                        remainingChunks--;
                    }
                }
            }

            System.out.println("  Done.");
            dataSocket.close();
            dataInputStream.close();
            fileOutputStream.close();
            return 0;
        }

        /**
         * Handler for {@code CD} command.
         * Change directory by updating the value of {@code pwd}.
         * Supports relative path on {@code pwd}.
         *
         * @param   request
         *          Name of the directory to move in {@code request[1]}.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred.
         */
        protected int handleCD(String[] request) throws IOException {
            // Check arguments.
            if (request.length > 2) {
                writeResponse(new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Too many arguments\n"
                ));
                return 1;
            }

            if (request.length == 1) {
                // Print pwd
                writeResponse(new Response(
                        ReturnCode.DIR_STATUS,
                        String.valueOf(pwd)
                ));

            } else {
                // Resolve target path.
                File targetPath = pwd.toPath().resolve(request[1]).toFile();

                if (!targetPath.exists()) {
                    // Target doesn't exist.
                    writeResponse(new Response(
                            ReturnCode.FILE_UNAVAILABLE,
                            "Directory doesn't exist"
                    ));
                    return 1;

                } else if (!targetPath.isDirectory()) {
                    // Target is not a directory.
                    writeResponse(new Response(
                            ReturnCode.FILE_UNAVAILABLE,
                            "Not a directory"
                    ));
                    return 1;
                }

                pwd = targetPath.getCanonicalFile();
                writeResponse(new Response(
                        ReturnCode.SUCCESS,
                        "Moved to " + pwd
                ));
            }
            return 0;
        }

    }


    // Default path for new users.
    protected final File defaultPath;

    // Maps request string to request handler.
    protected final Map<String, Method> requestHandlers;

    /* SR Parameters */
    protected final int senderTimeOut = 1;


    /**
     * Create new server on specific path (not yet started).
     *
     * @param   path
     *          Default path for new users.
     */
    public Server(String path) {
        defaultPath = new File(path);
        requestHandlers = new HashMap<>();
        try {
            requestHandlers.put("list", ClientHandler.class.getDeclaredMethod("handleLIST", String[].class));
            requestHandlers.put("get", ClientHandler.class.getDeclaredMethod("handleGET", String[].class));
            requestHandlers.put("put", ClientHandler.class.getDeclaredMethod("handlePUT", String[].class));
            requestHandlers.put("cd", ClientHandler.class.getDeclaredMethod("handleCD", String[].class));

        } catch (NoSuchMethodException e) {
            // This exception must not be thrown. Server goes down.
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Starts up server.
     *
     * @param   cmdPort
     *          Port number of command channel.
     * @param   dataPort
     *          Port number of data channel.
     *
     * @throws  IOException
     *          If IO exception occurred in server socket.
     */
    public void start(int cmdPort, int dataPort) throws IOException {
        ServerSocket serverCmdSocket = new ServerSocket(cmdPort);
        ServerSocket serverDataSocket = new ServerSocket(dataPort);
        System.out.println("Running.. ");
        while (true) {
            Socket cmdSocket = serverCmdSocket.accept();
            ClientHandler manager = new ClientHandler();
            manager.start(cmdSocket, serverDataSocket);
        }
    }

}
