package ftp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// Client-to-Server data chunk.
public class DataChunkC2S {

    public static final int maxDataSize = 1000;
    public static final int maxChunkSize = maxDataSize + 5;

    protected byte seqNo;
    protected short chkSum;
    protected short size;
    public byte[] data;


    public DataChunkC2S(
            byte seqNo,
            short size,
            byte[] data
    ) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
        this.size = size;
        this.data = data;
    }

    public DataChunkC2S(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        this.seqNo = wrapped.get();
        this.chkSum = wrapped.getShort();
        this.size = wrapped.getShort();
        this.data = new byte[wrapped.remaining()];
        wrapped.get(this.data);
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
        dataOutputStream.writeShort(size);
        dataOutputStream.write(data,0, Math.min(data.length, maxDataSize));
    }

    public boolean isError() {
        return chkSum != 0x0000;
    }

}
