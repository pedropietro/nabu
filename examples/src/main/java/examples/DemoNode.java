package examples;

import io.ipfs.cid.Cid;
import io.ipfs.multiaddr.MultiAddress;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.multiformats.Multiaddr;
import org.peergos.*;
import org.peergos.blockstore.Blockstore;
import org.peergos.blockstore.RamBlockstore;
import org.peergos.config.Config;
import org.peergos.config.IdentitySection;
import org.peergos.protocol.dht.RamRecordStore;
import org.peergos.protocol.dht.RecordStore;
import org.peergos.protocol.http.HttpProtocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Shared startup for the CLIs: one identity, a RAM blockstore, and a TCP listener. */
public final class DemoNode {
    public final EmbeddedIpfs ipfs;
    public final PrivKey privateKey;

    private DemoNode(EmbeddedIpfs ipfs, PrivKey privateKey) {
        this.ipfs = ipfs;
        this.privateKey = privateKey;
    }

    public static DemoNode start(boolean provideBlocks, List<MultiAddress> bootstrap, boolean bindAnyInterface) {
        return start(provideBlocks, bootstrap, bindAnyInterface, Optional.empty());
    }

    public static DemoNode start(boolean provideBlocks, List<MultiAddress> bootstrap, boolean bindAnyInterface,
                                 Optional<HttpProtocol.HttpRequestProcessor> handler) {
        int port = freePort();
        String host = bindAnyInterface ? "0.0.0.0" : "127.0.0.1";
        List<MultiAddress> swarm = List.of(new MultiAddress("/ip4/" + host + "/tcp/" + port));
        RecordStore records = new RamRecordStore();
        Blockstore blocks = new RamBlockstore();
        HostBuilder builder = new HostBuilder(new RamAddressBook()).generateIdentity();
        PrivKey privateKey = builder.getPrivateKey();
        PeerId peerId = builder.getPeerId();
        IdentitySection identity = new IdentitySection(privateKey.bytes(), peerId);
        BlockRequestAuthoriser authoriser = (c, p, a) -> CompletableFuture.completedFuture(true);
        EmbeddedIpfs ipfs = EmbeddedIpfs.build(records, blocks, provideBlocks, swarm, bootstrap,
                identity, authoriser, handler);
        ipfs.start(false);
        DemoNode node = new DemoNode(ipfs, privateKey);
        System.out.println("PeerId: " + peerId.toBase58());
        System.out.println("Listening: " + node.advertise());
        return node;
    }

    public String advertise() {
        PeerId id = ipfs.node.getPeerId();
        List<Multiaddr> listening = ipfs.node.listenAddresses();
        for (Multiaddr addr : listening) {
            String rendered = withPeer(addr.toString(), id);
            if (rendered.contains("/ip4/127.0.0.1/"))
                return rendered;
        }
        if (!listening.isEmpty())
            return withPeer(listening.get(0).toString(), id);
        return "/p2p/" + id.toBase58();
    }

    /** Remember a dial address. The multiaddr must end with /p2p/<peerId>. */
    public PeerId remember(String multiaddr) {
        Multiaddr addr = Multiaddr.fromString(multiaddr);
        PeerId peer = addr.getPeerId();
        ipfs.node.getAddressBook().addAddrs(peer, 0, addr).join();
        return peer;
    }

    public byte[] fetch(Cid cid, Set<PeerId> peers, int timeoutSeconds) {
        try {
            List<HashedBlock> blocks = CompletableFuture.supplyAsync(
                    () -> ipfs.getBlocks(List.of(new Want(cid)), peers, true))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            if (blocks.isEmpty())
                throw new IllegalStateException("Block not found: " + cid);
            return blocks.get(0).block;
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out fetching " + cid);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Could not fetch " + cid + ": " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted fetching " + cid);
        }
    }

    public static void printBlock(byte[] block) {
        System.out.println("Bytes: " + block.length);
        System.out.println(new String(block, StandardCharsets.UTF_8));
    }

    public static void blockUntilInterrupted() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static byte[] payload(List<String> rest, Scanner in) throws IOException {
        if (rest.size() > 1)
            throw new IllegalArgumentException("Pass at most one file");
        if (rest.size() == 1)
            return Files.readAllBytes(Path.of(rest.get(0)));
        System.out.println("Text to store:");
        String line = readLine(in);
        if (line.isEmpty())
            throw new IllegalArgumentException("Empty text");
        return line.getBytes(StandardCharsets.UTF_8);
    }

    /** One stdin line. A Windows pipe can deliver a UTF-8 BOM before the first line. */
    public static String readLine(Scanner in) {
        String line = in.nextLine();
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF')
            line = line.substring(1);
        return line;
    }

    public static Args parse(String[] args) {
        boolean publicDht = false;
        List<MultiAddress> bootstrap = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--public".equals(args[i]))
                publicDht = true;
            else if ("--bootstrap".equals(args[i])) {
                if (i + 1 >= args.length)
                    throw new IllegalArgumentException("--bootstrap needs a multiaddr");
                bootstrap.add(new MultiAddress(args[++i]));
            } else
                rest.add(args[i]);
        }
        if (publicDht && !bootstrap.isEmpty())
            throw new IllegalArgumentException("Pass either --public or --bootstrap");
        if (publicDht)
            bootstrap = new ArrayList<>(Config.defaultBootstrapNodes);
        return new Args(publicDht, bootstrap, rest);
    }

    public static final class Args {
        public final boolean publicDht;
        public final List<MultiAddress> bootstrap;
        public final List<String> rest;

        Args(boolean publicDht, List<MultiAddress> bootstrap, List<String> rest) {
            this.publicDht = publicDht;
            this.bootstrap = Collections.unmodifiableList(bootstrap);
            this.rest = Collections.unmodifiableList(rest);
        }
    }

    private static String withPeer(String addr, PeerId id) {
        if (addr.contains("/p2p/") || addr.contains("/ipfs/"))
            return addr;
        return addr + "/p2p/" + id.toBase58();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate a TCP port", e);
        }
    }
}
