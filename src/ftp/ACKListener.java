package ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Timer;
import java.util.concurrent.locks.ReentrantLock;

public class ACKListener implements Runnable {

    final BufferedReader dataInputStream;
    final String[] exceptionMsg;
    final int[] winBase;
    final int[] numBuffered;
    final byte[] firstSeqNo;
    final ReentrantLock lock;
    final Timer[] timers;

    public ACKListener(BufferedReader dataInputStream,
                       String[] exceptionMsg,
                       int[] winBase,
                       int[] numBuffered,
                       byte[] firstSeqNo,
                       ReentrantLock lock,
                       Timer[] timers) {
        this.dataInputStream = dataInputStream;
        this.exceptionMsg = exceptionMsg;
        this.winBase = winBase;
        this.numBuffered = numBuffered;
        this.firstSeqNo = firstSeqNo;
        this.lock = lock;
        this.timers = timers;
    }

    @Override
    public void run() {
        while (true) {
            try {
                String line = dataInputStream.readLine();
                if (line == null) break;
                int ACKed = Integer.parseInt(line);
                int idx;
                boolean isValidRange;

                try {
                    lock.lock();
                    int relativeACKed = (ACKed - firstSeqNo[0] + DataChunkC2S.numSeqNo) % DataChunkC2S.numSeqNo;
                    idx = (winBase[0] + relativeACKed) % DataChunkC2S.winSize;
                    isValidRange = relativeACKed < numBuffered[0];
                    if (isValidRange && timers[idx] != null) {
                        timers[idx].cancel();
                        timers[idx] = null;
                    }
                    synchronized (System.out) {
                        System.out.println("ACKed: " + ACKed);
                    }

                } finally {
                    lock.unlock();
                }

            } catch (IOException e) {
                exceptionMsg[0] = e.getMessage();
                break;
            }
        }
    }
}
