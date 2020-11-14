package ftp;

public enum ReturnCode {

    UNKNOWN(-1),

    // 200 Series
    // The requested action has been successfully completed.
    SUCCESS(200),               // Command okay.
    SERVICE_READY(220),
    SERVICE_CLOSING(221),

    // 500 Series
    // Syntax error, command unrecognized and the requested action did not take place.
    // This may include errors such as command line too long.
    UNRECOGNIZED(500),          // Syntax error, command unrecognized.
    FILE_UNAVAILABLE(550);      // Requested action not taken. File unavailable (e.g., file not found, no access).

    private final int codeNum;

    ReturnCode(int codeNum) {
        this.codeNum = codeNum;
    }

    public int getCodeNum() {
        return codeNum;
    }
}
