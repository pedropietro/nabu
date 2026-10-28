package examples;

import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.peergos.HttpProxyService;
import org.peergos.protocol.http.HttpProtocol;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

/**
 * p2p HTTP chat, the same shape as nabu-chat.
 * Each line is the body of a POST to the other peer. Incoming bodies are printed.
 */
public final class Chat {
    private Chat() {}

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
        DemoNode.Args opts = DemoNode.parse(args);
        if (!opts.rest.isEmpty())
            throw new IllegalArgumentException("Chat reads the peer and the messages from stdin");
        DemoNode node = DemoNode.start(false, opts.bootstrap, opts.publicDht, Optional.of(incoming()));
        PeerId peer;
        Multiaddr[] addrs;
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            if (!opts.publicDht && opts.bootstrap.size() == 1) {
                String dial = opts.bootstrap.get(0).toString();
                peer = node.remember(dial);
                addrs = new Multiaddr[]{Multiaddr.fromString(dial)};
            } else {
                System.out.println("PeerId or multiaddr:");
                String line = DemoNode.readLine(in).trim();
                if (line.isEmpty())
                    throw new IllegalArgumentException("Empty peer");
                if (line.contains("/p2p/") || line.contains("/ipfs/")) {
                    peer = node.remember(line);
                    addrs = new Multiaddr[]{Multiaddr.fromString(line)};
                } else {
                    peer = PeerId.fromBase58(line);
                    addrs = HttpProxyService.getAddresses(node.ipfs.node, node.ipfs.dht, Multihash.fromBase58(line));
                }
            }
            System.out.println("Talking to " + peer.toBase58());
            System.out.println("Type a message. quit to exit.");
            while (in.hasNextLine()) {
                String line = DemoNode.readLine(in);
                if ("quit".equals(line))
                    break;
                if (line.isEmpty())
                    continue;
                send(node, peer, addrs, line);
            }
        }
        System.exit(0);
    }

    private static void send(DemoNode node, PeerId peer, Multiaddr[] addrs, String text) {
        byte[] msg = text.getBytes(StandardCharsets.UTF_8);
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/",
                Unpooled.copiedBuffer(msg));
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, msg.length);
        try {
            HttpProtocol.Binding http = node.ipfs.p2pHttp.orElseThrow(
                    () -> new IllegalStateException("HTTP protocol missing"));
            HttpProtocol.HttpController proxier = http.dial(node.ipfs.node, peer, addrs).getController().join();
            proxier.send(req.retain()).join().release();
        } catch (Exception e) {
            if (req.refCnt() > 0)
                req.release();
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.out.println("Could not send: " + cause.getMessage());
        }
    }

    private static HttpProtocol.HttpRequestProcessor incoming() {
        return (s, req, h) -> {
            ByteBuf content = req.content();
            String text = content.getCharSequence(content.readerIndex(), content.readableBytes(),
                    StandardCharsets.UTF_8).toString();
            System.out.println("received: " + text);
            FullHttpResponse reply = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.buffer(0));
            reply.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            h.accept(reply.retain());
        };
    }

    private static void usage() {
        System.out.println("Chat [--bootstrap <multiaddr> | --public]");
    }
}
