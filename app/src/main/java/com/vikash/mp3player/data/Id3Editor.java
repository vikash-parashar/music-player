package com.vikash.mp3player.data;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites the title, artist and album of an MP3's ID3v2 tag and keeps every other frame
 * (cover art, lyrics, track number...) exactly as it was. No library, plain bytes.
 *
 * Written in Java with no Android imports so it can be unit-tested on any JVM.
 */
public final class Id3Editor {

    /** Thrown for tags this editor won't touch, so the file is never damaged. */
    public static final class UnsupportedTagException extends IOException {
        public UnsupportedTagException(String message) { super(message); }
    }

    private Id3Editor() {}

    /**
     * Reads an MP3 from {@code in} and writes it to {@code out} with new text frames.
     * {@code frames} maps a frame id (TIT2, TPE1, TALB) to its new value; an empty value
     * removes the frame. Frames not in the map are copied unchanged.
     */
    public static void rewrite(InputStream in, OutputStream out, Map<String, String> frames) throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] header = new byte[10];
        int read = readFully(data, header);

        int major = 3;
        Map<String, byte[]> kept = new LinkedHashMap<>();
        byte[] leftover = null; // bytes already read that belong to the audio

        if (read == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            major = header[3];
            int flags = header[5] & 0xFF;
            if (major != 3 && major != 4) throw new UnsupportedTagException("ID3v2." + major + " tags aren't supported");
            if ((flags & 0x80) != 0) throw new UnsupportedTagException("Unsynchronised tags aren't supported");
            int size = syncsafe(header, 6);
            byte[] body = new byte[size];
            if (readFully(data, body) != size) throw new IOException("File ends inside its tag");
            if (major == 4 && (flags & 0x10) != 0) { // v2.4 footer: 10 more bytes to drop
                byte[] footer = new byte[10];
                readFully(data, footer);
            }
            int pos = 0;
            if ((flags & 0x40) != 0) { // extended header: skip it
                int extSize = major == 4 ? syncsafe(body, 0) : int32(body, 0) + 4;
                pos = extSize;
            }
            while (pos + 10 <= body.length) {
                if (body[pos] == 0) break; // padding
                String id = new String(body, pos, 4, StandardCharsets.ISO_8859_1);
                int frameSize = major == 4 ? syncsafe(body, pos + 4) : int32(body, pos + 4);
                int total = 10 + frameSize;
                if (frameSize < 0 || pos + total > body.length) break; // corrupt: keep what we have
                byte[] raw = new byte[total];
                System.arraycopy(body, pos, raw, 0, total);
                // Several frames can share an id (e.g. two comments); keep them all.
                String key = id;
                int n = 1;
                while (kept.containsKey(key)) key = id + "#" + (n++);
                kept.put(key, raw);
                pos += total;
            }
        } else {
            leftover = new byte[read];
            System.arraycopy(header, 0, leftover, 0, read);
        }

        // Build the new frame list: replaced frames go first, in a fixed order.
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        for (Map.Entry<String, String> e : frames.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (!value.isEmpty()) tag.write(textFrame(e.getKey(), value, major));
        }
        for (Map.Entry<String, byte[]> e : kept.entrySet()) {
            String id = e.getKey().length() > 4 ? e.getKey().substring(0, 4) : e.getKey();
            if (!frames.containsKey(id)) tag.write(e.getValue());
        }
        byte[] padding = new byte[1024]; // room for later edits without moving the audio
        tag.write(padding);

        byte[] tagBytes = tag.toByteArray();
        out.write(new byte[] {'I', 'D', '3', (byte) major, 0, 0});
        out.write(syncsafeBytes(tagBytes.length));
        out.write(tagBytes);
        if (leftover != null) out.write(leftover);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = data.read(buf)) != -1) out.write(buf, 0, n);
        out.flush();
    }

    /**
     * Returns the lyrics stored in the tag (the first USLT frame), or null. Many lyrics taggers
     * put LRC text with [mm:ss] timestamps in here, which the player shows in sync.
     */
    public static String readLyrics(InputStream in) throws IOException {
        byte[] header = new byte[10];
        if (readFully(in, header) != 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;
        int major = header[3];
        int flags = header[5] & 0xFF;
        if ((major != 3 && major != 4) || (flags & 0x80) != 0) return null;
        int size = syncsafe(header, 6);
        if (size <= 0 || size > 64 * 1024 * 1024) return null;
        byte[] body = new byte[size];
        if (readFully(in, body) != size) return null;
        int pos = 0;
        if ((flags & 0x40) != 0) pos = major == 4 ? syncsafe(body, 0) : int32(body, 0) + 4;
        while (pos + 10 <= body.length && body[pos] != 0) {
            String id = new String(body, pos, 4, StandardCharsets.ISO_8859_1);
            int frameSize = major == 4 ? syncsafe(body, pos + 4) : int32(body, pos + 4);
            if (frameSize <= 0 || pos + 10 + frameSize > body.length) break;
            if (id.equals("USLT") && frameSize > 4) {
                int start = pos + 10;
                int end = start + frameSize;
                int enc = body[start];
                int p = start + 4; // encoding byte + 3-byte language
                boolean wide = enc == 1 || enc == 2;
                // Skip the content description, which ends with a null (two bytes for UTF-16).
                if (wide) {
                    while (p + 1 < end && !(body[p] == 0 && body[p + 1] == 0)) p += 2;
                    p += 2;
                } else {
                    while (p < end && body[p] != 0) p++;
                    p += 1;
                }
                if (p >= end) return null;
                String text;
                switch (enc) {
                    case 1: text = new String(body, p, end - p, StandardCharsets.UTF_16); break;
                    case 2: text = new String(body, p, end - p, StandardCharsets.UTF_16BE); break;
                    case 3: text = new String(body, p, end - p, StandardCharsets.UTF_8); break;
                    default: text = new String(body, p, end - p, StandardCharsets.ISO_8859_1);
                }
                text = text.replace("\u0000", "").trim();
                return text.isEmpty() ? null : text;
            }
            pos += 10 + frameSize;
        }
        return null;
    }

    /** A text frame in UTF-16 with a byte-order mark, which both ID3v2.3 and v2.4 accept. */
    static byte[] textFrame(String id, String value, int major) {
        byte[] text = value.getBytes(StandardCharsets.UTF_16); // big-endian with BOM
        int size = 1 + text.length + 2;
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(id.getBytes(StandardCharsets.ISO_8859_1), 0, 4);
        byte[] sizeBytes = major == 4 ? syncsafeBytes(size) : int32Bytes(size);
        f.write(sizeBytes, 0, 4);
        f.write(0);
        f.write(0); // frame flags
        f.write(1); // encoding: UTF-16 with BOM
        f.write(text, 0, text.length);
        f.write(0);
        f.write(0); // terminator
        return f.toByteArray();
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    static int syncsafe(byte[] b, int off) {
        return ((b[off] & 0x7F) << 21) | ((b[off + 1] & 0x7F) << 14) | ((b[off + 2] & 0x7F) << 7) | (b[off + 3] & 0x7F);
    }

    static int int32(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    static byte[] syncsafeBytes(int v) {
        return new byte[] {(byte) ((v >> 21) & 0x7F), (byte) ((v >> 14) & 0x7F), (byte) ((v >> 7) & 0x7F), (byte) (v & 0x7F)};
    }

    static byte[] int32Bytes(int v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }
}
