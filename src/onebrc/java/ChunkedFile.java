package onebrc.java;

import java.io.Closeable;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ChunkedFile implements Closeable {
    private static final int NAME_MAX_LEN = 100;
    private static final int TEMP_MAX_LEN = 5;
    private static final int LINE_MAX_LEN = NAME_MAX_LEN + TEMP_MAX_LEN + 2; // +2 for newline and semicolon

    private final FileChannel fileChannel;
    private final long fileSize;
    private final int chunkSize;
    private long chunkOffset = 0;

    public ChunkedFile(String filePath, int chunkSize) {
        try {
            this.fileChannel = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ);
            this.fileSize = fileChannel.size();
            this.chunkSize = chunkSize;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized MappedByteBuffer getNextChunk() {
        try {
            if (chunkOffset >= fileSize) {
                return null;
            }

            final MappedByteBuffer nextChunk;
            final int chunkSizeExtended = chunkSize + LINE_MAX_LEN;
            final long chunkEndExtended = chunkOffset + chunkSizeExtended;

            if (chunkEndExtended >= fileSize) {
                final long chunkSizeAligned = fileSize - chunkOffset;
                nextChunk = fileChannel.map(MapMode.READ_ONLY, chunkOffset, chunkSizeAligned);
            } else {
                nextChunk = fileChannel.map(MapMode.READ_ONLY, chunkOffset, chunkSizeExtended);
                nextChunk.position(chunkSize);

                while (nextChunk.get() != '\n') {
                }

                nextChunk.flip();
            }

            chunkOffset += nextChunk.limit();
            return nextChunk;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }
}
