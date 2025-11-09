package org.peergos.examples.chat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

/**
 * Value object representing a message in the repository backed chat example.
 */
public class ChatMessage {

    private final String sender;
    private final Instant timestamp;
    private final String content;

    public ChatMessage(String sender, Instant timestamp, String content) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.content = Objects.requireNonNull(content, "content");
    }

    public String getSender() {
        return sender;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getContent() {
        return content;
    }

    /**
     * Serialises this message to a stable byte representation that can be stored in a blockstore.
     */
    public byte[] toBytes() {
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String serialised = sender + "\n" + timestamp.toString() + "\n" + base64Content;
        return serialised.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Restores a {@link ChatMessage} from its serialised representation produced by {@link #toBytes()}.
     */
    public static ChatMessage fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data");
        String serialised = new String(data, StandardCharsets.UTF_8);
        String[] parts = serialised.split("\n", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid chat message encoding");
        }
        try {
            Instant timestamp = Instant.parse(parts[1]);
            String content = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            return new ChatMessage(parts[0], timestamp, content);
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid chat message encoding", ex);
        }
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + sender + ": " + content;
    }
}
