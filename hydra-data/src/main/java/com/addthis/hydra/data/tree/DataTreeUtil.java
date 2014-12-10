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
package com.addthis.hydra.data.tree;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.addthis.basis.util.ClosableIterator;

import com.addthis.bundle.util.ValueUtil;
import com.addthis.bundle.value.ValueObject;

public class DataTreeUtil {

    private static Object GLOB_OBJECT = new Object();

    public static Object getGlobObject() { return GLOB_OBJECT; }

    public static final DataTreeNode pathLocateFrom(DataTreeNode node, String[] path) {
        int plen = path.length;
        for (int i = 0; i < plen; i++) {
            node = node.getNode(path[i]);
            if (node == null || i == plen - 1) {
                return node;
            }
        }
        return node;
    }

    public static final @Nonnull List<DataTreeNode> pathLocateFrom(DataTreeNode node, ValueObject[] path) {
        if (path == null || node == null || path.length == 0) {
            return Collections.EMPTY_LIST;
        } else {
            List<DataTreeNode> current = new ArrayList<>();
            List<DataTreeNode> next = new ArrayList<>();
            current.add(node);
            return pathLocateFrom(path, 0, current, next);
        }
    }

    /**
     * Evaluate the path at the current node and return a list of results.
     *
     * @param node       current node
     * @param path       specification of path to evaluate
     */
    private static void pathLocateNext(DataTreeNode node, ValueObject path, List<DataTreeNode> results) {
        if (path.getObjectType() == ValueObject.TYPE.CUSTOM &&
            path.asNative() == GLOB_OBJECT) {
            ClosableIterator<DataTreeNode> iterator =  node.getIterator();
            try {
                while (iterator.hasNext()) {
                    results.add(iterator.next());
                }
            } finally {
                iterator.close();
            }
        } else {
            results.add(node.getNode(ValueUtil.asNativeString(path)));
        }
    }

    /**
     * Recursively traverse the paths from beginning to end and generate a result list of nodes.
     *
     * @param path      list of specifications of paths to evaluate
     * @param index     current position in the path list
     * @param current   list of input nodes
     * @return          list of output nodes
     */
    private static List<DataTreeNode> pathLocateFrom(ValueObject[] path, int index,
                                                           List<DataTreeNode> current,
                                                           List<DataTreeNode> next) {
        if (index < path.length) {
            for(DataTreeNode node : current) {
                pathLocateNext(node, path[index], next);
            }
            current.clear();
            return pathLocateFrom(path, index + 1, next, current);
        } else {
            return current;
        }
    }


}
