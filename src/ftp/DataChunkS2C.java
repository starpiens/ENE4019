package ftp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// Server-to-Client data chunk.
public class DataChunkS2C {

    public static final int maxDataSize = 1000;
    public static final int maxChunkSize = maxDataSize + 3;

    protected byte seqNo;
    protected short chkSum;
    public byte[] data;


    public DataChunkS2C(
            byte seqNo,
            byte[] data
    ) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
        this.data = data;
    }

    public DataChunkS2C(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        this.seqNo = wrapped.get();
        this.chkSum = wrapped.getShort();
        this.data = new byte[wrapped.remaining()];
        wrapped.get(this.data);
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
        dataOutputStream.write(data, 0, Math.min(data.length, maxDataSize));
    }

    public boolean isError() {
        return chkSum != 0x000;
    }

}
