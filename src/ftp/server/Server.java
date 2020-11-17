package ftp.server;

import ftp.Response;
import ftp.ReturnCode;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.exit;

public class Server {

    protected class ClientManager {

        protected class ClientDownException extends Exception {
        }


        // Networking
        protected Socket cmdSocket;
        protected BufferedReader cmdReader;
        protected DataOutputStream cmdOutStream;
        protected int dataPort;

        // Client status
        protected File pwd = defaultPath;


        public void start(Socket cmdSocket, int dataPort) {
            this.cmdSocket = cmdSocket;
            this.dataPort = dataPort;
            try {
                cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
                cmdOutStream = new DataOutputStream(cmdSocket.getOutputStream());
                System.out.println("Connection established: " + cmdSocket.getInetAddress());
                writeResponse(new Response(ReturnCode.SERVICE_READY, "Hello\n"));

                while (true) {
                    String[] request = getRequest();
                    Response response = processRequest(request);
                    writeResponse(response);
                    if (response.returnCode.equals(ReturnCode.SERVICE_CLOSING))
                        break;
                }

                cmdSocket.close();

            } catch (ClientDownException | IOException e) {
                System.out.println("Connection terminated: " + cmdSocket.getInetAddress());
            }
        }

        protected String[] getRequest() throws IOException, ClientDownException {
            String str = cmdReader.readLine();
            if (str == null) throw new ClientDownException();
            System.out.println("Request: " + str);
            return str.trim().split("[ ]+");
        }

        protected void writeResponse(Response response) throws IOException {
            String responseStr = response.toString();
            cmdOutStream.writeBytes(responseStr);
            // Print only first line
            System.out.println("Response: " + responseStr.substring(0, responseStr.indexOf('\n')));
        }

        protected Response processRequest(String[] request) throws ClientDownException {
            // Find method
            Method method = commands.get(request[0].toLowerCase());
            if (method == null) {
                return new Response(ReturnCode.UNRECOGNIZED, "Unknown command\n");
            } else {
                try {
                    // Call method
                    return (Response) method.invoke(this, (Object) request);

                } catch (IllegalAccessException e) {
                    // This must be not thrown. Server down.
                    e.printStackTrace();
                    exit(1);

                } catch (InvocationTargetException e) {
                    // Check whether class is down or not.
                    if (e.getCause().getClass().equals(ClientDownException.class)) {
                        throw (ClientDownException) e.getCause();
                    } else {
                        e.printStackTrace();
                        exit(1);
                    }
                }
            }
            return new Response("");
        }

        protected Response _list(String[] request) {
            // Check arguments.
            if (request.length != 2)
                return new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Single argument required\n"
                );

            // Resolve target path.
            File targetPath = pwd.toPath().resolve(request[1]).toFile();

            if (!targetPath.exists()) {
                // Target doesn't exist.
                return new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Directory doesn't exist"
                );
            } else if (!targetPath.isDirectory()) {
                // Target is not a directory.
                return new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Not a directory"
                );
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
                return response;
            } else {
                // In case of failure
                return new Response(
                        ReturnCode.FILE_UNAVAILABLE,
                        "Unable to access. Check if you have enough permission."
                );
            }
        }

        protected Response _get(String[] request) {


            return new Response(ReturnCode.SUCCESS, "OK\n");
        }

        protected Response _put(String[] request) {
            return new Response(ReturnCode.SUCCESS, "OK\n");
        }

        protected Response _cd(String[] request) throws IOException {
            // Check arguments.
            if (request.length > 2)
                return new Response(
                        ReturnCode.ARGUMENT_ERR,
                        "Too many arguments\n"
                );

            if (request.length == 1) {
                // Print pwd
                return new Response(
                        ReturnCode.DIR_STATUS,
                        String.valueOf(pwd)
                );

            } else {
                // Resolve target path.
                File targetPath = pwd.toPath().resolve(request[1]).toFile().getCanonicalFile();

                if (!targetPath.exists()) {
                    // Target doesn't exist.
                    return new Response(
                            ReturnCode.FILE_UNAVAILABLE,
                            "Directory doesn't exist"
                    );
                } else if (!targetPath.isDirectory()) {
                    // Target is not a directory.
                    return new Response(
                            ReturnCode.FILE_UNAVAILABLE,
                            "Not a directory"
                    );
                }

                pwd = targetPath;
                return new Response(
                        ReturnCode.SUCCESS,
                        "Moved to " + pwd
                );
            }
        }

        protected Response _quit(String[] request) throws ClientDownException {
            throw new ClientDownException();
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
        ServerSocket serverSocket = new ServerSocket(cmdPort);
        while (true) {
            Socket cmdSocket = serverSocket.accept();
            ClientManager manager = new ClientManager();
            manager.start(cmdSocket, dataPort);
        }
    }

}
