package examples;

import io.ipfs.cid.Cid;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import org.peergos.PeerAddresses;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Provider records.
 * Start {@code get} first. {@code put --bootstrap} announces the block to that node.
 * {@code get} then asks only for the CID.
 */
public final class Find {
    private Find() {}

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
        if ("put".equals(args[0]))
            put(opts);
        else if ("get".equals(args[0]))
            get(opts);
        else
            throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void put(DemoNode.Args opts) throws Exception {
        if (!opts.publicDht && opts.bootstrap.isEmpty())
            throw new IllegalArgumentException("Start Find get first, then pass its multiaddr with --bootstrap. Or pass --public.");
        DemoNode node = DemoNode.start(true, opts.bootstrap, opts.publicDht);
        byte[] data;
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            data = DemoNode.payload(opts.rest, in);
        }
        Cid cid = node.ipfs.blockstore.put(data, Cid.Codec.Raw).join();
        System.out.println("Announcing " + cid + " ...");
        node.ipfs.dht.provideBlock(cid, node.ipfs.node, PeerAddresses.fromHost(node.ipfs.node)).join();
        System.out.println("CID: " + cid);
        System.out.println("The getter only needs this CID. Leave this process running.");
        DemoNode.blockUntilInterrupted();
    }

    private static void get(DemoNode.Args opts) {
        if (!opts.bootstrap.isEmpty())
            throw new IllegalArgumentException("Start Find get first. Pass the multiaddr it prints to Find put --bootstrap.");
        if (!opts.rest.isEmpty())
            throw new IllegalArgumentException("Find get reads the CID from stdin");
        DemoNode node = DemoNode.start(false, opts.bootstrap, opts.publicDht);
        System.out.println("Start the other process with: put --bootstrap " + node.advertise());
        System.out.println("CID:");
        String cidLine;
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            cidLine = DemoNode.readLine(in).trim();
        }
        if (cidLine.isEmpty())
            throw new IllegalArgumentException("Empty CID");
        Cid cid = Cid.decode(cidLine);
        List<PeerAddresses> providers = node.ipfs.dht.findProviders(cid, node.ipfs.node, 5).join();
        if (providers.isEmpty())
            System.out.println("No provider records.");
        else
            System.out.println("Providers:");
        for (PeerAddresses provider : providers) {
            System.out.println("  " + provider.peerId.toBase58() + " " + provider.addresses);
            remember(node, provider);
        }
        int timeout = opts.publicDht ? 120 : 30;
        byte[] block = node.fetch(cid, Set.of(), timeout);
        System.out.println("Fetched " + cid + " by CID.");
        DemoNode.printBlock(block);
        System.exit(0);
    }

    private static void remember(DemoNode node, PeerAddresses provider) {
        if (provider.addresses.isEmpty())
            return;
        PeerId peer = PeerId.fromBase58(provider.peerId.toBase58());
        Multiaddr[] addrs = provider.addresses.toArray(new Multiaddr[0]);
        node.ipfs.node.getAddressBook().addAddrs(peer, 0, addrs).join();
    }

    private static void usage() {
        System.out.println("Find get [--public]");
        System.out.println("Find put (--bootstrap <multiaddr> | --public) [file]");
    }
}
