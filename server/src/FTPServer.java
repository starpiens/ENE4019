import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {

    protected void serveClient(Socket ctrlSocket) throws IOException {
        BufferedReader ctrlReader = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
        DataOutputStream ctrlOutStream = new DataOutputStream(ctrlSocket.getOutputStream());
        while (true) {
            String commandLine = ctrlReader.readLine();
            System.out.println(commandLine);
            ctrlOutStream.writeBytes(commandLine + '\n');
        }
    }

    public void start(int ctrlPort, int dataPort) throws IOException {
        ServerSocket serverSocket = new ServerSocket(ctrlPort);
        while (true) {
            Socket ctrlSocket = serverSocket.accept();
            serveClient(ctrlSocket);
        }
    }

    public static void main(String[] args) throws IOException {
        int ctrlPort = 2020;
        int dataPort = 2021;
        try {
            ctrlPort = Integer.parseInt(args[0]);
            dataPort = Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        FTPServer server = new FTPServer();
        server.start(ctrlPort, dataPort);
    }
}
