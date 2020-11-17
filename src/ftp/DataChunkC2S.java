package ftp;

import java.io.DataOutputStream;
import java.io.IOException;

// Client-to-Server data chunk.
public class DataChunkC2S {

    public static final int maxDataSize = 1000;
    public static final int maxChunkSize = maxDataSize + 5;

    protected byte seqNo;
    protected short chkSum;
    protected short size;
    protected byte[] dataChunk;


    public DataChunkC2S(
            byte seqNo,
            short size,
            byte[] dataChunk
    ) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
        this.size = size;
        this.dataChunk = dataChunk;
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
        dataOutputStream.writeShort(size);
        dataOutputStream.write(dataChunk,0, Math.min(dataChunk.length, maxDataSize));
    }

    public boolean isError() {
        return chkSum != 0x0000;
    }

}
