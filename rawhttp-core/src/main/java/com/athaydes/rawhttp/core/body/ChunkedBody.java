package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;
import com.athaydes.rawhttp.core.RawHttpHeaders;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

/**
 * This class encodes the contents of a {@link InputStream} with the "chunked" Transfer-Encoding.
 * <p>
 * The {@link InputStream} contents are expected to NOT be encoded. To parse the contents of a stream which is
 * already "chunk" encoded, use {@link com.athaydes.rawhttp.core.ChunkedBodyContents}.
 */
public class ChunkedBody extends HttpMessageBody {

    private final InputStream stream;
    private final int chunkLength;

    /**
     * Create a new {@link ChunkedBody} to encode the contents of the given stream.
     * <p>
     * The stream is read lazily, so it shouldn't be closed until this body is consumed.
     *
     * @param contentType Content-Type of the stream contents
     * @param stream      content to encode
     * @param chunkLength the length of each chunk
     */
    public ChunkedBody(@Nullable String contentType, InputStream stream, int chunkLength) {
        super(contentType);
        this.stream = stream;
        this.chunkLength = chunkLength;
    }

    /**
     * @return empty. "chunked"-encoded bodies are normally used for content for which the length is unknown.
     */
    @Override
    protected OptionalLong getContentLength() {
        return OptionalLong.empty();
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(BodyReader.BodyType.CHUNKED,
                new ChunkedInputStream(stream, chunkLength), null, false);
    }

    @Override
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.Builder.newBuilder(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        builder.overwrite("Transfer-Encoding", "chunked");
        builder.remove("Content-Length");
        return builder.build();
    }

    private static class ChunkedInputStream extends InputStream {

        private final InputStream stream;
        private final int chunkSize;

        private byte[] buffer = new byte[0];
        private int index = 0;
        private boolean terminated = false;

        ChunkedInputStream(InputStream stream, int chunkSize) {
            this.stream = stream;
            this.chunkSize = chunkSize;
        }

        private byte[] nextChunk() throws IOException {
            byte[] chunkData = new byte[chunkSize];
            int bytesRead = stream.read(chunkData);
            if (bytesRead <= 0) {
                terminated = true;
                bytesRead = 0;
            }

            byte[] chunkSizeBytes = (Integer.toString(bytesRead, 16) + "\r\n").getBytes(StandardCharsets.US_ASCII);

            byte[] chunk = new byte[chunkSizeBytes.length + bytesRead + 2];
            System.arraycopy(chunkSizeBytes, 0, chunk, 0, chunkSizeBytes.length);
            if (bytesRead > 0) {
                System.arraycopy(chunkData, 0, chunk, chunkSizeBytes.length, bytesRead);
            }
            chunk[chunk.length - 2] = '\r';
            chunk[chunk.length - 1] = '\n';
            return chunk;
        }

        @Override
        public int read() throws IOException {
            if (index >= buffer.length) {
                if (terminated) {
                    return -1;
                }
                buffer = nextChunk();
                if (buffer.length == 0) {
                    return -1;
                }
                index = 0;
            }
            return buffer[index++];
        }

        @Override
        public boolean markSupported() {
            return false;
        }

    }

}
