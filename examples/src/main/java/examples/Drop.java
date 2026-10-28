package examples;

import io.ipfs.cid.Cid;
import io.libp2p.core.PeerId;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;
import java.util.Set;

/**
 * Content addressing and Bitswap.
 * {@code put} hashes bytes to a raw CID and serves that block.
 * {@code get} dials the printed multiaddr and fetches the CID from that peer.
 */
public final class Drop {
    private Drop() {}

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
        if (opts.publicDht || !opts.bootstrap.isEmpty())
            throw new IllegalArgumentException("Drop uses a direct multiaddr and an empty bootstrap list");
        if ("put".equals(args[0]))
            put(opts);
        else if ("get".equals(args[0]))
            get(opts);
        else
            throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void put(DemoNode.Args opts) throws Exception {
        DemoNode node = DemoNode.start(false, Collections.emptyList(), false);
        byte[] data;
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            data = DemoNode.payload(opts.rest, in);
        }
        Cid cid = node.ipfs.blockstore.put(data, Cid.Codec.Raw).join();
        System.out.println("CID: " + cid);
        System.out.println("Same bytes always hash to this CID. Leave this process running.");
        System.out.println("Other process: get " + node.advertise() + " " + cid);
        DemoNode.blockUntilInterrupted();
    }

    private static void get(DemoNode.Args opts) {
        if (opts.rest.size() != 2)
            throw new IllegalArgumentException("Usage: get <multiaddr> <cid>");
        DemoNode node = DemoNode.start(false, Collections.emptyList(), false);
        PeerId peer = node.remember(opts.rest.get(0));
        Cid cid = Cid.decode(opts.rest.get(1));
        byte[] block = node.fetch(cid, Set.of(peer), 30);
        System.out.println("Fetched " + cid + " from " + peer.toBase58());
        DemoNode.printBlock(block);
        System.exit(0);
    }

    private static void usage() {
        System.out.println("Drop put [file]");
        System.out.println("Drop get <multiaddr> <cid>");
    }
}
