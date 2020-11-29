package ftp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Client-to-Server data chunk.
 */
public class DataChunkC2S {

    public static final int headerSize = 5;
    public static final int maxDataSize = 1000;
    public static final int maxChunkSize = maxDataSize + headerSize;

    /* SR parameters */
    public static final int maxSeqNo = 15;
    public static final int winSize = 5;

    protected byte seqNo;       // Sequence number.
    protected short chkSum;     // Checksum value. It is regarded that bit error has occurred, if the value != 0x0000.
    protected short size;       // Size of data.
    public byte[] data;

    public byte getSeqNo() { return seqNo; }
    public short getSize() { return size; }


    public DataChunkC2S(
            byte seqNo,
            byte[] data
    ) {
        this.seqNo = seqNo;
        this.size = (short) data.length;
        this.data = data;
        this.chkSum = 0x0000;
    }

    public DataChunkC2S(byte[] header) {
        ByteBuffer wrapped = ByteBuffer.wrap(header);
        this.seqNo = wrapped.get();
        this.chkSum = wrapped.getShort();
        this.size = wrapped.getShort();
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
        dataOutputStream.writeShort(size);
        dataOutputStream.write(data,0, data.length);
    }

    public boolean isError() {
        return chkSum != 0x0000;
    }

}
