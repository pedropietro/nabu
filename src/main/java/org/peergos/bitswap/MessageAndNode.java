package org.peergos.bitswap;

import io.ipfs.cid.*;
import org.peergos.bitswap.pb.*;

public class MessageAndNode {
    public final MessageOuterClass.Message msg;
    public final Cid nodeId;

    public MessageAndNode(MessageOuterClass.Message msg, Cid nodeId) {
        this.msg = msg;
        this.nodeId = nodeId;
    }
}
