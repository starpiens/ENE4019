package FTP;

public enum ReturnCode {

    SUCCESS(200),
    FAIL(400);

    private final int code;

    ReturnCode(int code) {
        this.code = code;
    }

}
