import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {

    protected void serveClient(Socket commandSocket) throws IOException {
        BufferedReader commandReader = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
        while (true) {
            String commandLine = commandReader.readLine();
            System.out.print(commandLine);
            
        }
    }

    public void start(int commandPort, int dataPort) throws IOException {
        ServerSocket serverSocket = new ServerSocket(2020);

        while (true) {
            Socket commandSocket = serverSocket.accept();
            serveClient(commandSocket);
        }
    }

    public static void main(String[] args) {
        FTPServer server = new FTPServer();
        try {
            server.start(2020, 2021);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
