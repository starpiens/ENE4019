package ftp;

import java.util.HashMap;
import java.util.Map;

public class Response {

    /**
     * Response message format:
     * returnCode message\n
     * message\n
     * message\n
     * \n\n
     */

    public ReturnCode returnCode;
    public String message;

    protected static final Map<Integer, ReturnCode> int2code = new HashMap<>();


    public Response(ReturnCode returnCode) {
        this.returnCode = returnCode;
    }

    public Response(ReturnCode returnCode, String message) {
        this(returnCode);
        this.message = message;
    }

    public Response(String responseStr) {
        // Construct `int2code`.
        if (int2code.isEmpty()) {
            for (ReturnCode code : ReturnCode.values()) {
                int2code.put(code.getCodeNum(), code);
            }
        }

        // Set `returnCode`.
        String returnCodeStr = responseStr.substring(0, responseStr.indexOf(' '));
        Integer returnCodeInt = Integer.valueOf(returnCodeStr);
        this.returnCode = int2code.get(returnCodeInt);
        if (this.returnCode == null) {
            this.returnCode = ReturnCode.UNKNOWN;
        }

        // Set `message`.
        this.message = responseStr.substring(responseStr.indexOf(' ') + 1);
    }

    public String toString() {
        String string = returnCode.getCodeNum() + " " + message + "\n";
        if (!message.endsWith("\n")) string += "\n";
        return string;
    }

}
