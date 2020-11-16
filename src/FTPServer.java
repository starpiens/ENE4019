import ftp.server.Server;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public class FTPServer {

    public static void main(String[] args) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        int cmdPort = 2020;
        int dataPort = 2021;
        try {
            cmdPort = Integer.parseInt(args[0]);
            dataPort = Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        Server server = new Server(System.getProperty("user.dir"));
        server.start(cmdPort, dataPort);
    }

}
