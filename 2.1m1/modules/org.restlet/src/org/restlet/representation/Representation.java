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

package org.restlet.representation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Date;

import org.restlet.data.Disposition;
import org.restlet.data.MediaType;
import org.restlet.data.Range;
import org.restlet.data.Tag;
import org.restlet.engine.io.BioUtils;
import org.restlet.engine.util.DateUtils;

/**
 * Current or intended state of a resource. The content of a representation can
 * be retrieved several times if there is a stable and accessible source, like a
 * local file or a string. When the representation is obtained via a temporary
 * source like a network socket, its content can only be retrieved once. The
 * "transient" and "available" properties are available to help you figure out
 * those aspects at runtime.<br>
 * <br>
 * For performance purpose, it is essential that a minimal overhead occurs upon
 * initialization. The main overhead must only occur during invocation of
 * content processing methods (write, getStream, getChannel and toString).<br>
 * <br>
 * "REST components perform actions on a resource by using a representation to
 * capture the current or intended state of that resource and transferring that
 * representation between components. A representation is a sequence of bytes,
 * plus representation metadata to describe those bytes. Other commonly used but
 * less precise names for a representation include: document, file, and HTTP
 * message entity, instance, or variant." Roy T. Fielding
 * 
 * @see <a href=
 *      "http://roy.gbiv.com/pubs/dissertation/rest_arch_style.htm#sec_5_2_1_2"
 *      >Source dissertation</a>
 * @author Jerome Louvel
 */
public abstract class Representation extends RepresentationInfo {
    /**
     * Indicates that the size of the representation can't be known in advance.
     */
    public static final long UNKNOWN_SIZE = -1L;

    /** Indicates if the representation's content is potentially available. */
    private volatile boolean available;

    // [ifndef gwt] member
    /**
     * The representation digest if any.
     */
    private volatile org.restlet.data.Digest digest;

    /** The disposition characteristics of the representation. */
    private volatile Disposition disposition;

    /** The expiration date. */
    private volatile Date expirationDate;

    /** Indicates if the representation's content is transient. */
    private volatile boolean isTransient;

    /**
     * Indicates where in the full content the partial content available should
     * be applied.
     */
    private volatile Range range;

    /**
     * The expected size. Dynamic representations can have any size, but
     * sometimes we can know in advance the expected size. If this expected size
     * is specified by the user, it has a higher priority than any size that can
     * be guessed by the representation (like a file size).
     */
    private volatile long size;

    /**
     * Default constructor.
     */
    public Representation() {
        this(null);
    }

    /**
     * Constructor.
     * 
     * @param mediaType
     *            The media type.
     */
    public Representation(MediaType mediaType) {
        super(mediaType);
        this.available = true;
        this.disposition = null;
        this.isTransient = false;
        this.size = UNKNOWN_SIZE;
        this.expirationDate = null;
        // [ifndef gwt]
        this.digest = null;
        this.range = null;
        // [enddef]
    }

    /**
     * Constructor.
     * 
     * @param mediaType
     *            The media type.
     * @param modificationDate
     *            The modification date.
     */
    public Representation(MediaType mediaType, Date modificationDate) {
        this(mediaType, modificationDate, null);
    }

    /**
     * Constructor.
     * 
     * @param mediaType
     *            The media type.
     * @param modificationDate
     *            The modification date.
     * @param tag
     *            The tag.
     */
    public Representation(MediaType mediaType, Date modificationDate, Tag tag) {
        super(mediaType, modificationDate, tag);
    }

    /**
     * Constructor.
     * 
     * @param mediaType
     *            The media type.
     * @param tag
     *            The tag.
     */
    public Representation(MediaType mediaType, Tag tag) {
        this(mediaType, null, tag);
    }

    /**
     * Constructor from a variant.
     * 
     * @param variant
     *            The variant to copy.
     * @param modificationDate
     *            The modification date.
     */
    public Representation(Variant variant, Date modificationDate) {
        this(variant, modificationDate, null);
    }

    /**
     * Constructor from a variant.
     * 
     * @param variant
     *            The variant to copy.
     * @param modificationDate
     *            The modification date.
     * @param tag
     *            The tag.
     */
    public Representation(Variant variant, Date modificationDate, Tag tag) {
        setCharacterSet(variant.getCharacterSet());
        setEncodings(variant.getEncodings());
        setLocationRef(variant.getLocationRef());
        setLanguages(variant.getLanguages());
        setMediaType(variant.getMediaType());
        setModificationDate(modificationDate);
        setTag(tag);
    }

    /**
     * Constructor from a variant.
     * 
     * @param variant
     *            The variant to copy.
     * @param tag
     *            The tag.
     */
    public Representation(Variant variant, Tag tag) {
        this(variant, null, tag);
    }

    /**
     * Exhaust the content of the representation by reading it and silently
     * discarding anything read. By default, it relies on {@link #getStream()}
     * and closes the retrieved stream in the end.
     * 
     * @return The number of bytes consumed or -1 if unknown.
     */
    public long exhaust() throws IOException {
        long result = -1L;

        // [ifndef gwt]
        if (isAvailable()) {
            InputStream is = getStream();
            result = BioUtils.exhaust(is);
            is.close();
        }
        // [enddef]

        return result;
    }

    /**
     * Returns the size effectively available. This returns the same value as
     * {@link #getSize()} if no range is defined, otherwise it returns the size
     * of the range using {@link Range#getSize()}.
     * 
     * @return The available size.
     */
    public long getAvailableSize() {
        return BioUtils.getAvailableSize(this);
    }

    // [ifndef gwt] member
    /**
     * Returns a channel with the representation's content.<br>
     * If it is supported by a file, a read-only instance of FileChannel is
     * returned.<br>
     * This method is ensured to return a fresh channel for each invocation
     * unless it is a transient representation, in which case null is returned.
     * 
     * @return A channel with the representation's content.
     * @throws IOException
     */
    public abstract java.nio.channels.ReadableByteChannel getChannel()
            throws IOException;

    // [ifndef gwt] method
    /**
     * Returns the representation digest if any.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-MD5" header.
     * 
     * @return The representation digest or null.
     */
    public org.restlet.data.Digest getDigest() {
        return this.digest;
    }

    /**
     * Returns the disposition characteristics of the representation.
     * 
     * @return The disposition characteristics of the representation.
     */
    public Disposition getDisposition() {
        return disposition;
    }

    /**
     * Returns the future date when this representation expire. If this
     * information is not known, returns null.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Expires" header.
     * 
     * @return The expiration date.
     */
    public Date getExpirationDate() {
        return this.expirationDate;
    }

    /**
     * Returns the range where in the full content the partial content available
     * should be applied.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-Range" header.
     * 
     * @return The content range or null if the full content is available.
     */
    public Range getRange() {
        return this.range;
    }

    /**
     * Returns a characters reader with the representation's content. This
     * method is ensured to return a fresh reader for each invocation unless it
     * is a transient representation, in which case null is returned. If the
     * representation has no character set defined, the system's default one
     * will be used.
     * 
     * @return A reader with the representation's content.
     * @throws IOException
     */
    public abstract Reader getReader() throws IOException;

    // [ifndef gwt] method
    /**
     * Returns the NIO registration of the channel with its selector. You can
     * modify this registration to be called back when some readable content is
     * available. Note that the listener will keep being called back until you
     * suspend or cancel the registration returned by this method.
     * 
     * @return The NIO registration.
     * @throws IOException
     * @see #isSelectable()
     */
    public org.restlet.util.SelectionRegistration getRegistration()
            throws IOException {
        if (isSelectable()) {
            return ((org.restlet.engine.io.SelectionChannel) getChannel())
                    .getRegistration();
        } else {
            throw new IllegalStateException(
                    "The representation isn't selectable");
        }
    }

    /**
     * Returns the total size in bytes if known, UNKNOWN_SIZE (-1) otherwise.
     * When ranges are used, this might not be the actual size available. For
     * this purpose, you can use the {@link #getAvailableSize()} method.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-Length" header.
     * 
     * @return The size in bytes if known, UNKNOWN_SIZE (-1) otherwise.
     */
    public long getSize() {
        return this.size;
    }

    /**
     * Returns a stream with the representation's content. This method is
     * ensured to return a fresh stream for each invocation unless it is a
     * transient representation, in which case null is returned.
     * 
     * @return A stream with the representation's content.
     * @throws IOException
     */
    public abstract InputStream getStream() throws IOException;

    // [ifndef gwt] method
    /**
     * Converts the representation to a string value. Be careful when using this
     * method as the conversion of large content to a string fully stored in
     * memory can result in OutOfMemoryErrors being thrown.
     * 
     * @return The representation as a string value.
     */
    public String getText() throws IOException {
        String result = null;

        if (isAvailable()) {
            if (getSize() == 0) {
                result = "";
            } else {
                java.io.StringWriter sw = new java.io.StringWriter();
                write(sw);
                result = sw.toString();
            }
        }

        return result;
    }

    /**
     * Indicates if some fresh content is potentially available, without having
     * to actually call one of the content manipulation method like getStream()
     * that would actually consume it. Note that when the size of a
     * representation is 0 is a not considered available. However, sometimes the
     * size isn't known until a read attempt is made, so availability doesn't
     * guarantee a non empty content.<br>
     * <br>
     * This is especially useful for transient representation whose content can
     * only be accessed once and also when the size of the representation is not
     * known in advance.
     * 
     * @return True if some fresh content is available.
     */
    public boolean isAvailable() {
        return this.available && (getSize() != 0);
    }

    // [ifndef gwt] method
    /**
     * Indicates if the representation content supports NIO selection. In this
     * case, the
     * {@link org.restlet.engine.connector.ConnectionController#register(java.nio.channels.SelectableChannel, int, org.restlet.util.SelectionListener)}
     * method can be called to be notified when new content is ready for
     * reading.
     * 
     * @return True if the representation content supports NIO selection.
     * @see #register(org.restlet.util.SelectionListener)
     */
    public boolean isSelectable() {
        try {
            return getChannel() instanceof org.restlet.engine.io.SelectionChannel;
        } catch (IOException e) {
            return false;
        }
    }

    // [ifdef gwt] method uncomment
    // /**
    // * Converts the representation to a string value. Be careful when using
    // * this method as the conversion of large content to a string fully
    // * stored in memory can result in OutOfMemoryErrors being thrown.
    // *
    // * @return The representation as a string value.
    // */
    // public abstract String getText() throws IOException;

    /**
     * Indicates if the representation's content is transient, which means that
     * it can be obtained only once. This is often the case with representations
     * transmitted via network sockets for example. In such case, if you need to
     * read the content several times, you need to cache it first, for example
     * into memory or into a file.
     * 
     * @return True if the representation's content is transient.
     */
    public boolean isTransient() {
        return this.isTransient;
    }

    /**
     * Releases the representation's content and all associated objects like
     * sockets, channels or files. If the representation is transient and hasn't
     * been read yet, all the remaining content will be discarded, any open
     * socket, channel, file or similar source of content will be immediately
     * closed. The representation is also no more available.
     */
    public void release() {
        this.available = false;
    }

    /**
     * Indicates if some fresh content is available.
     * 
     * @param available
     *            True if some fresh content is available.
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    // [ifndef gwt] method
    /**
     * Sets the representation digest.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-MD5" header.
     * 
     * @param digest
     *            The representation digest.
     */
    public void setDigest(org.restlet.data.Digest digest) {
        this.digest = digest;
    }

    /**
     * Sets the disposition characteristics of the representation.
     * 
     * @param disposition
     *            The disposition characteristics of the representation.
     */
    public void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    /**
     * Sets the future date when this representation expire. If this information
     * is not known, pass null.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Expires" header.
     * 
     * @param expirationDate
     *            The expiration date.
     */
    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = DateUtils.unmodifiable(expirationDate);
    }

    /**
     * Sets the range where in the full content the partial content available
     * should be applied.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-Range" header.
     * 
     * @param range
     *            The content range.
     */
    public void setRange(Range range) {
        this.range = range;
    }

    /**
     * Sets the expected size in bytes if known, -1 otherwise. For this purpose,
     * you can use the {@link #getAvailableSize()} method.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "Content-Length" header.
     * 
     * @param expectedSize
     *            The expected size in bytes if known, -1 otherwise.
     */
    public void setSize(long expectedSize) {
        this.size = expectedSize;
    }

    /**
     * Indicates if the representation's content is transient.
     * 
     * @param isTransient
     *            True if the representation's content is transient.
     */
    public void setTransient(boolean isTransient) {
        this.isTransient = isTransient;
    }

    // [ifndef gwt] member
    /**
     * Writes the representation to a characters writer. This method is ensured
     * to write the full content for each invocation unless it is a transient
     * representation, in which case an exception is thrown.<br>
     * <br>
     * Note that the class implementing this method shouldn't flush or close the
     * given {@link java.io.Writer} after writing to it as this will be handled
     * by the Restlet connectors automatically.
     * 
     * @param writer
     *            The characters writer.
     * @throws IOException
     */
    public abstract void write(java.io.Writer writer) throws IOException;

    // [ifndef gwt] member
    /**
     * Writes the representation to a byte channel. This method is ensured to
     * write the full content for each invocation unless it is a transient
     * representation, in which case an exception is thrown.
     * 
     * @param writableChannel
     *            A writable byte channel.
     * @throws IOException
     */
    public abstract void write(
            java.nio.channels.WritableByteChannel writableChannel)
            throws IOException;

    // [ifndef gwt] member
    /**
     * Writes the representation to a byte stream. This method is ensured to
     * write the full content for each invocation unless it is a transient
     * representation, in which case an exception is thrown.<br>
     * <br>
     * Note that the class implementing this method shouldn't flush or close the
     * given {@link OutputStream} after writing to it as this will be handled by
     * the Restlet connectors automatically.
     * 
     * @param outputStream
     *            The output stream.
     * @throws IOException
     */
    public abstract void write(OutputStream outputStream) throws IOException;

}
