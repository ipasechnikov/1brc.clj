package onebrc.java;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

public class ChunkedFileReader {
    private static final int NAME_MAX_LEN = 100;

    private final ChunkedFile file;
    private final ByteBuffer nameBuffer = ByteBuffer.allocate(NAME_MAX_LEN);
    private MappedByteBuffer currentChunk;

    public ChunkedFileReader(ChunkedFile file) {
        this.file = file;
    }

    public boolean hasRemaining() {
        if (currentChunk != null && currentChunk.hasRemaining()) {
            return true;
        }

        currentChunk = file.getNextChunk();
        return currentChunk != null;
    }

    public ByteBuffer readName() {
        nameBuffer.clear();
        for (byte b = currentChunk.get(); b != ';'; b = currentChunk.get()) {
            nameBuffer.put(b);
        }
        nameBuffer.flip();
        return nameBuffer;
    }

    public int readTemp() {
        int sign = 1;
        int temp = 0;
        for (byte b = currentChunk.get(); b != '\n'; b = currentChunk.get()) {
            switch (b) {
                case '-':
                    sign = -1;
                    break;
                case '.':
                    break;
                default:
                    temp = (temp * 10) + (b - '0');
                    break;
            }
        }
        return temp * sign;
    }
}
