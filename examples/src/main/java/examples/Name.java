package examples;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import org.peergos.protocol.ipns.IpnsRecord;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * IPNS: a mutable name over immutable blocks.
 * Start {@code resolve} first. {@code publish --bootstrap} writes each line as a new raw block
 * and publishes that CID under this node's PeerId, with an increasing sequence.
 */
public final class Name {
    private static final int TTL_HOURS = 24;

    private Name() {}

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            usage();
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0)
            throw new IllegalArgumentException("Missing command");
        DemoNode.Args opts = DemoNode.parse(Arrays.copyOfRange(args, 1, args.length));
        if ("publish".equals(args[0]))
            publish(opts);
        else if ("resolve".equals(args[0]))
            resolve(opts);
        else
            throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void publish(DemoNode.Args opts) throws Exception {
        if (!opts.publicDht && opts.bootstrap.isEmpty())
            throw new IllegalArgumentException("Start Name resolve first, then pass its multiaddr with --bootstrap. Or pass --public.");
        if (!opts.rest.isEmpty())
            throw new IllegalArgumentException("Name publish reads lines from stdin");
        DemoNode node = DemoNode.start(true, opts.bootstrap, opts.publicDht);
        System.out.println("This PeerId is the IPNS name. Each line is a new block and a new sequence.");
        long sequence = 0;
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (in.hasNextLine()) {
                String line = DemoNode.readLine(in);
                if ("quit".equals(line))
                    break;
                if (line.isEmpty())
                    continue;
                sequence++;
                byte[] data = line.getBytes(StandardCharsets.UTF_8);
                Cid cid = node.ipfs.blockstore.put(data, Cid.Codec.Raw).join();
                byte[] value = cid.toString().getBytes(StandardCharsets.UTF_8);
                System.out.println("Publishing sequence " + sequence + " -> " + cid);
                int stored = node.ipfs.publishValue(node.privateKey, value, Optional.empty(), Optional.empty(),
                        sequence, TTL_HOURS).join();
                System.out.println("CID: " + cid);
                System.out.println("IPNS record stored on " + stored + " peers.");
            }
        }
        System.exit(0);
    }

    private static void resolve(DemoNode.Args opts) {
        if (!opts.bootstrap.isEmpty())
            throw new IllegalArgumentException("Start Name resolve first. Pass the multiaddr it prints to Name publish --bootstrap.");
        if (opts.rest.size() > 1)
            throw new IllegalArgumentException("Usage: resolve [--public] [peerId]");
        DemoNode node = DemoNode.start(false, opts.bootstrap, opts.publicDht);
        System.out.println("Start the other process with: publish --bootstrap " + node.advertise());
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            String publisher = opts.rest.isEmpty() ? "" : opts.rest.get(0);
            if (publisher.isEmpty()) {
                System.out.println("Publisher PeerId:");
                publisher = DemoNode.readLine(in).trim();
            }
            if (publisher.isEmpty())
                throw new IllegalArgumentException("Empty PeerId");
            PeerId publisherId = PeerId.fromBase58(publisher);
            Multihash publisherHash = Multihash.fromBase58(publisher);
            System.out.println("Commands: resolve | get <cid> | quit");
            while (in.hasNextLine()) {
                String line = DemoNode.readLine(in).trim();
                if (line.isEmpty() || "resolve".equals(line))
                    resolveOnce(node, publisherHash, publisherId, opts.publicDht);
                else if ("quit".equals(line))
                    break;
                else if (line.startsWith("get "))
                    fetch(node, Cid.decode(line.substring(4).trim()), publisherId, opts.publicDht);
                else
                    System.out.println("Commands: resolve | get <cid> | quit");
            }
        }
        System.exit(0);
    }

    private static void resolveOnce(DemoNode node, Multihash publisher, PeerId publisherId, boolean publicDht) {
        List<IpnsRecord> records = node.ipfs.resolveRecords(publisher, 1);
        if (records.isEmpty()) {
            System.out.println("No IPNS record yet.");
            return;
        }
        IpnsRecord latest = records.get(records.size() - 1);
        String cidText = new String(latest.value, StandardCharsets.UTF_8);
        System.out.println("IPNS sequence " + latest.sequence);
        System.out.println("CID: " + cidText);
        fetch(node, Cid.decode(cidText), publisherId, publicDht);
    }

    private static void fetch(DemoNode node, Cid cid, PeerId publisher, boolean publicDht) {
        Set<PeerId> peers = publicDht ? Set.of() : Set.of(publisher);
        int timeout = publicDht ? 120 : 30;
        byte[] block = node.fetch(cid, peers, timeout);
        System.out.println("Block " + cid);
        DemoNode.printBlock(block);
    }

    private static void usage() {
        System.out.println("Name resolve [--public] [peerId]");
        System.out.println("Name publish (--bootstrap <multiaddr> | --public)");
    }
}
