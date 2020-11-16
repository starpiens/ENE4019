import ftp.client.Client;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

public class FTPClient {

    public static void main(String[] args) throws IOException {
        String host = "127.0.0.1";
        int cmdPort = 2020;
        int dataPort = 2021;
        try {
            host = args[0];
            cmdPort = Integer.parseInt(args[1]);
            dataPort = Integer.parseInt(args[2]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        Client client = new Client();
        while (true) {
            try {
                client.start(host, cmdPort, dataPort);
                break;
            } catch (ConnectException exception) {
                System.out.println("Failed to connect server. Retrying.. ");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
