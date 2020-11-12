import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class FTPClient {

    public void start(String host, int ctrlPort, int dataPort) throws IOException {
        Socket socket = new Socket(host, ctrlPort);
        BufferedReader stdReader = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader ctrlReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        DataOutputStream ctrlOutStream = new DataOutputStream(socket.getOutputStream());

        while (true) {
            String commandLine = stdReader.readLine();
            if (commandLine.split("[ ]+")[0].equalsIgnoreCase("quit")) {
                break;
            }
            ctrlOutStream.writeBytes(commandLine + '\n');
            String responseLine = ctrlReader.readLine();
            System.out.println(responseLine);
        }

        socket.close();
    }

    public static void main(String[] args) throws IOException {
        String host = "127.0.0.1";
        int ctrlPort = 2020;
        int dataPort = 2021;
        try {
            host = args[0];
            ctrlPort = Integer.parseInt(args[1]);
            dataPort = Integer.parseInt(args[2]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        FTPClient client = new FTPClient();
        client.start(host, ctrlPort, dataPort);
    }
}
