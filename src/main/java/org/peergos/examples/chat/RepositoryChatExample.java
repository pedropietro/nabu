package org.peergos.examples.chat;

import io.ipfs.cid.Cid;
import org.peergos.blockstore.FileBlockstore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Entry point showcasing how to build a repository backed chat on top of the local IPFS blockstore APIs.
 *
 * <p>The example creates a repository for each participant (Alice and Bob) and stores their messages.
 * The messages are persisted in a {@link org.peergos.blockstore.Blockstore} implementation, returning a
 * {@link Cid} for each entry that can be exchanged over any transport layer.</p>
 */
public final class RepositoryChatExample {

    private RepositoryChatExample() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting repository-backed chat example\n");

        Path aliceRepositoryPath = Files.createTempDirectory("alice-chat-repo");
        Path bobRepositoryPath = Files.createTempDirectory("bob-chat-repo");

        try {
            FileBlockstore aliceBlockstore = new FileBlockstore(aliceRepositoryPath);
            FileBlockstore bobBlockstore = new FileBlockstore(bobRepositoryPath);

            ChatRepository aliceRepository = new ChatRepository(aliceBlockstore, Clock.systemUTC());
            ChatRepository bobRepository = new ChatRepository(bobBlockstore, Clock.systemUTC());

            List<Cid> conversationIds = new ArrayList<>();

            Cid greetingId = aliceRepository.save("Alice", "Olá Bob! Este é um exemplo de chat usando um repositório IPFS.");
            conversationIds.add(greetingId);
            System.out.println("Alice armazenou a mensagem " + greetingId);

            Cid replyId = bobRepository.save("Bob", "Oi Alice! Consigo ler sua mensagem usando o identificador gerado.");
            conversationIds.add(replyId);
            System.out.println("Bob armazenou a mensagem " + replyId);

            Cid followUpId = aliceRepository.save("Alice", "Perfeito! Podemos compartilhar apenas os CIDs para reconstruir a conversa.");
            conversationIds.add(followUpId);
            System.out.println("Alice armazenou a mensagem " + followUpId + "\n");

            List<ChatMessage> recoveredMessages = new ArrayList<>();
            recoveredMessages.addAll(aliceRepository.loadConversation(conversationIds));
            recoveredMessages.addAll(bobRepository.loadConversation(conversationIds));

            recoveredMessages.sort((left, right) -> left.getTimestamp().compareTo(right.getTimestamp()));

            System.out.println("Transcrição completa usando o repositório local:\n");
            for (ChatMessage message : recoveredMessages) {
                System.out.println(message);
            }
        } finally {
            deleteRecursively(aliceRepositoryPath);
            deleteRecursively(bobRepositoryPath);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
