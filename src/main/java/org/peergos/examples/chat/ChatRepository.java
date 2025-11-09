package org.peergos.examples.chat;

import io.ipfs.cid.Cid;
import org.peergos.blockstore.Blockstore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple repository abstraction that persists chat messages inside an IPFS {@link Blockstore}.
 */
public class ChatRepository {

    private final Blockstore blockstore;
    private final Clock clock;

    public ChatRepository(Blockstore blockstore, Clock clock) {
        this.blockstore = Objects.requireNonNull(blockstore, "blockstore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Stores the message content in the blockstore and returns the generated {@link Cid}.
     */
    public Cid save(String sender, String message) {
        ChatMessage chatMessage = new ChatMessage(sender, clock.instant(), message);
        return blockstore.put(chatMessage.toBytes(), Cid.Codec.Raw).join();
    }

    /**
     * Retrieves a message from the blockstore.
     */
    public Optional<ChatMessage> find(Cid cid) {
        return blockstore.get(cid)
                .join()
                .map(ChatMessage::fromBytes);
    }

    /**
     * Loads a conversation using a list of message identifiers.
     */
    public List<ChatMessage> loadConversation(List<Cid> messageIds) {
        List<ChatMessage> messages = new ArrayList<>();
        for (Cid messageId : messageIds) {
            find(messageId).ifPresent(messages::add);
        }
        return messages;
    }
}
