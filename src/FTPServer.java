import ftp.server.Server;

import java.io.IOException;

public class FTPServer {

    public static void main(String[] args) throws IOException {
        int cmdPort = 2020;
        int dataPort = 2021;
        try {
            cmdPort = Integer.parseInt(args[0]);
            dataPort = Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        Server server = new Server();
        server.start(cmdPort, dataPort);
    }

}
