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
 * This class provides FTP server functionalities,
 * such as opening up new connection and executing some file-related commands from remote clients.
 * Indeed, it is not yet implemented via multi-thread, so it cannot serve multiple clients in parallel.
 */
public class Server {

    /**
     * A class which serves single client.
     * When new client comes, Server creates multiple instances of ClientManager.
     */
    protected class ClientManager {

        /** Networking */
        protected Socket cmdSocket;
        protected BufferedReader cmdReader;
        protected DataOutputStream cmdOutStream;
        protected ServerSocket serverDataSocket;

        /** Client status */
        protected File pwd = defaultPath;

        /**
         * Start serving a client.
         *
         * @param   cmdSocket
         *          Opened socket for command channel.
         * @param   serverDataSocket
         *          Opened server socket for data channel.
         */
        public void start(Socket cmdSocket, ServerSocket serverDataSocket) {
            this.cmdSocket = cmdSocket;
            this.serverDataSocket = serverDataSocket;
            try {
                // Open up IO stream.
                cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
                cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());
                System.err.println("Connection established: " + cmdSocket.getInetAddress());
                // Say hello!
                writeResponse(new Response(ReturnCode.SERVICE_READY, "Hello\n"));

                int processRequestReturnCode;
                do {
                    // Get request, and process it.
                    String[] request = getRequest();
                    processRequestReturnCode = handleRequest(request);
                } while (processRequestReturnCode != -1);

                // Connection closed normally.
                System.err.println("Connection with " + cmdSocket.getInetAddress() + " closed.");

            } catch (IOException e) {
                // Connection closed because of IOException.
                System.err.println("Connection with " + cmdSocket.getInetAddress()
                        + " closed, because IO problem occurred.");
                System.err.println("Details: " + e.getMessage());

            } finally {
                // Cleanup connection.
                try {
                    cmdReader.close();
                    cmdOutStream.close();
                    cmdSocket.close();
                } catch (IOException ignored) {
                }
            }
        }

        /**
         * Get request from a client.
         *
         * @return  Array of string of request which is split by space as delimiter.
         *          Name of a command is stored at index 0, and other arguments follow.
         *
         * @throws  IOException
         *          IO exception occurred.
         */
        protected String[] getRequest() throws IOException {
            String str = cmdReader.readLine();
            if (str == null) throw new IOException();
            System.out.println("Request: " + str);
            return str.trim().split("[ ]+");
        }

        /**
         * Write response to a client.
         *
         * @param   response
         *          Response to write.
         *
         * @throws  IOException
         *          IO exception occurred.
         */
        protected void writeResponse(Response response) throws IOException {
            String responseStr = response.toString();
            cmdOutStream.writeBytes(responseStr);
            // Print only first line
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
         * @return  0 if request is normally handled, and -1 if client wants to quit.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int handleRequest(String[] request) throws IOException {
            // Is the request is quit?
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
                // Call method
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
        protected int _list(String[] request) throws IOException {
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
         * Supports relative path on {@code pwd}.
         *
         * @param   request
         *          Name of file(s) starting at index 1.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int _get(String[] request) throws IOException {
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
         *          Name of file(s) starting at index 1.
         *
         * @return  0 in case of success, non-zero value in case of failure.
         *
         * @throws  IOException
         *          If an IO exception occurred while writing the response.
         */
        protected int _put(String[] request) throws IOException {
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

            // Check availability.
            if (targetFile.exists()) {
                // Target name already exists.
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

            // Setup IO streams.
            Socket dataSocket = serverDataSocket.accept();
            DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
            FileOutputStream fileOutputStream = new FileOutputStream(targetFile);

            // Start receiving.
            for (byte seqNo = 0; seqNo * DataChunkC2S.maxDataSize < targetLength; seqNo++) {
                byte[] bytes = dataInputStream.readNBytes(DataChunkC2S.maxChunkSize);
                System.out.print("#");
                DataChunkC2S chunk = new DataChunkC2S(bytes);
                fileOutputStream.write(chunk.data);
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
         *          If an IO exception occurred while writing the response.
         */
        protected int _cd(String[] request) throws IOException {
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
    protected File defaultPath;

    // Maps request string to request handler.
    protected final Map<String, Method> requestHandlers;


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
            requestHandlers.put("list", ClientManager.class.getDeclaredMethod("_list", String[].class));
            requestHandlers.put("get", ClientManager.class.getDeclaredMethod("_get", String[].class));
            requestHandlers.put("put", ClientManager.class.getDeclaredMethod("_put", String[].class));
            requestHandlers.put("cd", ClientManager.class.getDeclaredMethod("_cd", String[].class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
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
            ClientManager manager = new ClientManager();
            manager.start(cmdSocket, serverDataSocket);
        }
    }

}
