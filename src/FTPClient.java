import ftp.client.Client;

import java.io.IOException;

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
        client.start(host, cmdPort, dataPort);
    }

}
