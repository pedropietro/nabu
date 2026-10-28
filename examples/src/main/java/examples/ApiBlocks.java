package examples;

import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;
import org.peergos.PeerAddresses;
import org.peergos.client.NabuClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Same block operations as {@code examples/api.md}, through {@link NabuClient}:
 * id, block/put (raw), block/get, dht/findprovs.
 */
public final class ApiBlocks {
    private ApiBlocks() {}

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        String api = "/ip4/127.0.0.1/tcp/5001";
        Path file = null;
        if (args.length == 1) {
            if (args[0].startsWith("/"))
                api = args[0];
            else
                file = Path.of(args[0]);
        } else if (args.length >= 2) {
            api = args[0];
            file = Path.of(args[1]);
        }
        byte[] data;
        if (file != null)
            data = Files.readAllBytes(file);
        else {
            System.out.println("Text to store:");
            try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
                String line = DemoNode.readLine(in);
                if (line.isEmpty())
                    throw new IllegalArgumentException("Empty text");
                data = line.getBytes(StandardCharsets.UTF_8);
            }
        }

        NabuClient client = new NabuClient(api);
        System.out.println("PeerId: " + client.id().toBase58());
        Cid cid = client.putBlock(data, Optional.of("raw"));
        System.out.println("CID: " + cid);
        byte[] fetched = client.getBlock(cid, Optional.empty());
        if (!Arrays.equals(data, fetched))
            throw new IllegalStateException("block/get returned different bytes");
        System.out.println("block/get matches block/put");
        DemoNode.printBlock(fetched);
        printProviders(client, cid);
        if (file != null)
            System.out.println("Kubo, same file and raw codec: ipfs block put --format=raw " + file);
        System.exit(0);
    }

    private static void printProviders(NabuClient client, Multihash cid) {
        try {
            List<PeerAddresses> providers = client.findProviders(cid);
            if (providers.isEmpty())
                System.out.println("dht/findprovs: no providers");
            else
                System.out.println("dht/findprovs:");
            for (PeerAddresses provider : providers)
                System.out.println("  " + provider.peerId.toBase58() + " " + provider.addresses);
        } catch (Exception e) {
            String message = e.getMessage();
            System.out.println("dht/findprovs: " + (message == null ? "no providers" : message));
        }
    }
}
