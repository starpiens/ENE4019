package ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Timer;
import java.util.concurrent.locks.ReentrantLock;

public class ACKListener implements Runnable {

    final BufferedReader dataInputStream;
    final String[] exceptionMsg;
    volatile int[] winBase;
    volatile int[] numBuffered;
    volatile byte[] firstSeqNo;
    final ReentrantLock windowLock;
    final ReentrantLock logLock;
    volatile Timer[] timers;

    public ACKListener(BufferedReader dataInputStream,
                       String[] exceptionMsg,
                       int[] winBase,
                       int[] numBuffered,
                       byte[] firstSeqNo,
                       ReentrantLock windowLock,
                       ReentrantLock logLock,
                       Timer[] timers) {
        this.dataInputStream = dataInputStream;
        this.exceptionMsg = exceptionMsg;
        this.winBase = winBase;
        this.numBuffered = numBuffered;
        this.firstSeqNo = firstSeqNo;
        this.windowLock = windowLock;
        this.logLock = logLock;
        this.timers = timers;
    }

    @Override
    public void run() {
        while (true) {
            try {
                String line = dataInputStream.readLine();
                if (line == null) continue;
                int ACKed = Integer.parseInt(line);
                int idx;
                boolean isValidRange;

                try {
                    windowLock.lock();
                    int relativeACKed = (ACKed - firstSeqNo[0] + DataChunkC2S.numSeqNo) % DataChunkC2S.numSeqNo;
                    idx = (winBase[0] + relativeACKed) % DataChunkC2S.winSize;
                    isValidRange = relativeACKed < numBuffered[0];
                    if (isValidRange && timers[idx] != null) {
                        timers[idx].cancel();
                        timers[idx] = null;
                    }
                    logLock.lock();
                    System.out.println("ACKed: " + ACKed + " <-- Server");


                } finally {
                    windowLock.unlock();
                    logLock.unlock();
                }

            } catch (IOException e) {
                exceptionMsg[0] = e.getMessage();
                break;
            }
        }
    }
}
