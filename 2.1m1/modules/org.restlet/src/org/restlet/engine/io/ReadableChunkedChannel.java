/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Level;

import org.restlet.Context;

// [excludes gwt]
/**
 * Readable byte channel capable of decoding chunked entities.
 */
public class ReadableChunkedChannel extends
        WrapperChannel<ReadableBufferedChannel> implements ReadableByteChannel {

    /** The remaining chunk size that should be read from the source channel. */
    private volatile long remainingChunkSize;

    /** The chunk state. */
    private volatile ChunkState chunkState;

    /**
     * Constructor.
     * 
     * @param source
     *            The source channel.
     */
    public ReadableChunkedChannel(ReadableBufferedChannel source) {
        super(source);
        this.remainingChunkSize = 0;
        this.chunkState = ChunkState.SIZE;
    }

    /**
     * Reads some bytes and put them into the destination buffer. The bytes come
     * from the underlying channel.
     * 
     * @param dst
     *            The destination buffer.
     * @return The number of bytes read, or -1 if the end of the channel has
     *         been reached.
     */
    public int read(ByteBuffer dst) throws IOException {
        int result = 0;
        boolean tryAgain = true;

        while (tryAgain) {
            switch (this.chunkState) {

            case SIZE:
                if (getWrappedChannel().fillLineBuilder()) {
                    // The chunk size line was fully read into the line builder
                    int length = getWrappedChannel().getLineBuilder().length();

                    if (length == 0) {
                        throw new IOException(
                                "An empty chunk size line was detected");
                    }

                    int index = (getWrappedChannel().getLineBuilder()
                            .indexOf(";"));
                    index = (index == -1) ? getWrappedChannel()
                            .getLineBuilder().length() : index;

                    try {
                        this.remainingChunkSize = Long.parseLong(
                                getWrappedChannel().getLineBuilder()
                                        .substring(0, index).trim(), 16);

                        if (Context.getCurrentLogger().isLoggable(Level.FINE)) {
                            Context.getCurrentLogger().fine(
                                    "New chunk detected. Size: "
                                            + this.remainingChunkSize);
                        }
                    } catch (NumberFormatException ex) {
                        throw new IOException("\""
                                + getWrappedChannel().getLineBuilder()
                                + "\" has an invalid chunk size");
                    } finally {
                        getWrappedChannel().clearLineBuilder();
                    }

                    if (this.remainingChunkSize == 0) {
                        this.chunkState = ChunkState.TRAILER;
                    } else {
                        this.chunkState = ChunkState.DATA;
                    }
                } else {
                    tryAgain = false;
                }
                break;

            case DATA:
                if (this.remainingChunkSize > 0) {
                    if (this.remainingChunkSize < dst.remaining()) {
                        dst.limit((int) (this.remainingChunkSize + dst
                                .position()));
                    }

                    result = getWrappedChannel().read(dst);
                    tryAgain = false;

                    if (result > 0) {
                        this.remainingChunkSize -= result;
                    } else {
                        if (Context.getCurrentLogger().isLoggable(Level.FINE)) {
                            Context.getCurrentLogger().fine(
                                    "No chunk data read");
                        }
                    }
                } else if (this.remainingChunkSize == 0) {
                    // Try to read the chunk end delimiter
                    if (getWrappedChannel().fillLineBuilder()) {
                        // Done, can read the next chunk
                        getWrappedChannel().clearLineBuilder();
                        this.chunkState = ChunkState.SIZE;
                    } else {
                        tryAgain = false;
                    }
                }

                break;

            case TRAILER:
                // TODO
                this.chunkState = ChunkState.END;
                break;

            case END:
                if (getWrappedChannel().fillLineBuilder()) {
                    if (getWrappedChannel().getLineBuilder().length() == 0) {
                        result = -1;
                        tryAgain = false;
                    }
                }
                break;
            }

        }

        if (getWrappedChannel() instanceof ReadableBufferedChannel) {
            ((ReadableBufferedChannel) getWrappedChannel()).postRead(result);
        }

        return result;
    }
}
