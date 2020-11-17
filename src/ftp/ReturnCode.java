package ftp;

public enum ReturnCode {

    // Unknown code
    UNKNOWN(-1),

    // 200 Series
    // The requested action has been successfully completed.
    SUCCESS(200),               // Command okay.
    DIR_STATUS(212),            // Directory status.
    SERVICE_READY(220),         // Service ready for new user.
    SERVICE_CLOSING(221),       // Service closing control connection.

    // 500 Series
    // Syntax error, command unrecognized and the requested action did not take place.
    // This may include errors such as command line too long.
    UNRECOGNIZED(500),          // Syntax error, command unrecognized.
    ARGUMENT_ERR(501),          // Syntax error in parameters or arguments.
    FILE_UNAVAILABLE(550);      // Requested action not taken. File unavailable (e.g., file not found, no access).

    private final int codeNum;

    ReturnCode(int codeNum) {
        this.codeNum = codeNum;
    }

    public int getCodeNum() {
        return codeNum;
    }
}
