package ftp.server;

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

public class Server {

    protected class ClientManager {

        protected class ConnectionDownException extends Exception {
        }


        // Networking
        protected Socket cmdSocket;
        protected BufferedReader cmdReader;
        protected DataOutputStream cmdOutStream;
        protected ServerSocket serverDataSocket;

        // Client status
        protected File pwd = defaultPath;


        public void start(Socket cmdSocket, ServerSocket serverDataSocket) {
            this.cmdSocket = cmdSocket;
            this.serverDataSocket = serverDataSocket;
            try {
                cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
                cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());
                System.out.println("Connection established: " + cmdSocket.getInetAddress());
                writeResponse(new Response(ReturnCode.SERVICE_READY, "Hello\n"));

                while (true) {
                    String[] request = getRequest();
                    processRequest(request);
                }

            } catch (ConnectionDownException | IOException ignored) {
            } finally {
                System.out.println("Connection terminated: " + cmdSocket.getInetAddress());
                try {
                    cmdSocket.close();
                } catch (IOException ignored) {
                }
            }
        }

        protected String[] getRequest() throws IOException, ConnectionDownException {
            String str = cmdReader.readLine();
            if (str == null) throw new ConnectionDownException();
            System.out.println("Request: " + str);
            return str.trim().split("[ ]+");
        }

        protected void writeResponse(Response response) throws IOException {
            String responseStr = response.toString();
            cmdOutStream.writeBytes(responseStr);
            // Print only first line
            System.out.println("Response: " + responseStr.substring(0, responseStr.indexOf('\n')));
        }

        protected void processRequest(String[] request) throws ConnectionDownException, IOException {
            // Find method
            Method method = commands.get(request[0].toLowerCase());

            if (method == null) {
                writeResponse(new Response(ReturnCode.UNRECOGNIZED, "Unknown command\n"));

            } else {
                try {
                    // Call method
                    method.invoke(this, (Object) request);

                } catch (IllegalAccessException e) {
                    // This must be not thrown. Server down.
                    e.printStackTrace();
                    exit(1);

                } catch (InvocationTargetException e) {
                    // Is connection down?
                    if (e.getCause().getClass().equals(ConnectionDownException.class)
                            || e.getCause().getClass().equals(IOException.class)) {
                        throw (ConnectionDownException) e.getCause();

                    } else {
                        e.printStackTrace();
                        exit(1);
                    }
                }
            }
        }

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
            dataOutputStream.close();
            fileInputStream.close();
            return 0;
        }

        protected int _put(String[] request) {

            return 0;
        }

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

        protected int _quit(String[] request) throws ConnectionDownException {
            throw new ConnectionDownException();
        }

    }


    // Default path for new user
    protected File defaultPath;

    // Available requests
    protected final Map<String, Method> commands;


    public Server(String path) {
        defaultPath = new File(path);
        commands = new HashMap<>();
        try {
            commands.put("list", ClientManager.class.getDeclaredMethod("_list", String[].class));
            commands.put("get", ClientManager.class.getDeclaredMethod("_get", String[].class));
            commands.put("put", ClientManager.class.getDeclaredMethod("_put", String[].class));
            commands.put("cd", ClientManager.class.getDeclaredMethod("_cd", String[].class));
            commands.put("quit", ClientManager.class.getDeclaredMethod("_quit", String[].class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public void start(int cmdPort, int dataPort) throws IOException {
        ServerSocket serverCmdSocket = new ServerSocket(cmdPort);
        ServerSocket serverDataSocket = new ServerSocket(dataPort);
        while (true) {
            Socket cmdSocket = serverCmdSocket.accept();
            ClientManager manager = new ClientManager();
            manager.start(cmdSocket, serverDataSocket);
        }
    }

}
