/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.data.tree.prop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.addthis.basis.util.Varint;

import com.addthis.bundle.value.ValueFactory;
import com.addthis.bundle.value.ValueObject;
import com.addthis.codec.Codec;
import com.addthis.codec.CodecBin2;
import com.addthis.hydra.data.tree.DataTreeNode;
import com.addthis.hydra.data.tree.DataTreeNodeUpdater;
import com.addthis.hydra.data.tree.ReadTreeNode;
import com.addthis.hydra.data.tree.TreeDataParameters;
import com.addthis.hydra.data.tree.TreeNodeData;
import com.addthis.hydra.data.tree.TreeNodeDataDeferredOperation;
import com.addthis.hydra.data.util.KeyTopper;
import com.addthis.hydra.store.kv.KeyCoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

/**
 * TODO complete
 */
public class DataLimitHot extends TreeNodeData<DataLimitHot.Config> {

    /**
     * This data attachment <span class="hydra-summary">limits child nodes to hot values</span>.
     *
     * @user-reference
     * @hydra-name limit.hot
     */
    public static final class Config extends TreeDataParameters<DataLimitHot> {

        /**
         * Maximum number of child nodes allowed.
         */
        @Codec.Set(codable = true)
        private int size;

        @Override
        public DataLimitHot newInstance() {
            DataLimitHot dc = new DataLimitHot();
            dc.size = size;
            dc.top = new KeyTopper();
            return dc;
        }
    }

    @Codec.Set(codable = true)
    private int size;
    @Codec.Set(codable = true)
    private long deleted;
    @Codec.Set(codable = true)
    private KeyTopper top;

    @Override
    public boolean updateChildData(DataTreeNodeUpdater state, DataTreeNode tn, Config conf) {
        return false;
    }

    public static class DataLimitHotDeferredOperation extends TreeNodeDataDeferredOperation {

        final DataTreeNode parentNode;
        final String dropped;

        DataLimitHotDeferredOperation(DataTreeNode parentNode, String dropped) {
            this.parentNode = parentNode;
            this.dropped = dropped;
        }

        @Override
        public void run() {
            if (parentNode.deleteNode(dropped)) {
                // yay
            } else {
                // boo
            }
        }
    }

    @Override
    public boolean updateParentData(DataTreeNodeUpdater state, DataTreeNode parentNode,
            DataTreeNode childNode,
            List<TreeNodeDataDeferredOperation> deferredOps) {
        try {
            String key = childNode.getName();
            String dropped;
            synchronized (top) {
                dropped = top.update(key, childNode.getCounter(), size);
            }
            if (dropped != null) {
                deferredOps.add(new DataLimitHotDeferredOperation(parentNode, dropped));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public List<DataTreeNode> getNodes(DataTreeNode parent, String key) {
        if (key == null) {
            return null;
        }
        if (key.equals("hit") || key.equals("node")) {
            KeyTopper map = top;
            Entry<String, Long>[] top = map.getSortedEntries();
            ArrayList<DataTreeNode> ret = new ArrayList<>(top.length);
            for (Entry<String, Long> e : top) {
                DataTreeNode node = parent.getNode(e.getKey());
                if (node != null) {
                    ret.add(node);
                }
            }
            return ret;
        } else if (key.equals("vhit")) {
            Entry<String, Long>[] list = top.getSortedEntries();
            ArrayList<DataTreeNode> ret = new ArrayList<>(list.length);
            for (Entry<String, Long> e : list) {
                ret.add(new VirtualTreeNode(e.getKey(), e.getValue()));
            }
            return ret;
        } else if (key.equals("phit")) {
            Entry<String, Long>[] list = top.getSortedEntries();
            ArrayList<DataTreeNode> ret = new ArrayList<>(list.length);
            for (Entry<String, Long> e : list) {
                DataTreeNode node = parent.getNode(e.getKey());
                if (node != null) {
                    node = ((ReadTreeNode) node).getCloneWithCount(e.getValue());
                    ret.add(node);
                }
            }
            return ret;
        }
        return null;
    }

    @Override
    public byte[] bytesEncode(long version) {
        byte[] bytes = null;
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
        try {
            byte[] topBytes = top.bytesEncode(version);
            Varint.writeUnsignedVarInt(topBytes.length, buf);
            buf.writeBytes(topBytes);
            Varint.writeUnsignedVarInt(size, buf);
            Varint.writeUnsignedVarLong(deleted, buf);
            bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
        } finally {
            buf.release();
        }
        return bytes;
    }

    @Override
    public void bytesDecode(byte[] b, long version) {
        if (version < KeyCoder.EncodeType.KEYTOPPER.ordinal()) {
            try {
                codec.decode(this, b);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            top = new KeyTopper();
            ByteBuf buf = Unpooled.wrappedBuffer(b);
            try {
                int topBytesLength = Varint.readUnsignedVarInt(buf);
                if (topBytesLength > 0) {
                    byte[] topBytes = new byte[topBytesLength];
                    buf.readBytes(topBytes);
                    top.bytesDecode(topBytes, version);
                }
                size = Varint.readUnsignedVarInt(buf);
                deleted = Varint.readUnsignedVarLong(buf);
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public ValueObject getValue(String key) {
        return ValueFactory.create(deleted);
    }
}
