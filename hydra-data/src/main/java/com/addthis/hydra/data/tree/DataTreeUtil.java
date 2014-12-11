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
import java.util.Iterator;
import java.util.List;

import com.addthis.basis.util.ClosableIterator;

import com.addthis.bundle.util.ValueUtil;
import com.addthis.bundle.value.ValueObject;

import com.google.common.collect.Iterators;

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

    public static final @Nonnull Iterator<DataTreeNode> pathLocateFrom(DataTreeNode node, ValueObject[] path) {
        if (path == null || node == null || path.length == 0) {
            return Iterators.emptyIterator();
        } else {
            return pathLocateFrom(path, 0, Iterators.singletonIterator(node));
        }
    }

    /**
     * Recursively traverse the paths from beginning to end and generate iteration of output nodes.
     * Separate the iterator into the first element and the rest of the elements. The common
     * case is an iterator of size one and we handle this case separately to eliminate the
     * overhead of merging iterators of iterators.
     *
     * @param path      list of specifications of paths to evaluate
     * @param index     current position in the path list
     * @param current   iterator of input nodes
     * @return          iterator of output nodes
     */
    private static Iterator<DataTreeNode> pathLocateFrom(ValueObject[] path, int index,
                                                         Iterator<DataTreeNode> current) {
        if (index < path.length) {
            if (current.hasNext()) {
                DataTreeNode head = current.next();
                Iterator<DataTreeNode> nextHead = pathLocateNext(head, path[index]);
                if (current.hasNext()) {
                    return Iterators.concat(nextHead, Iterators.concat(
                            Iterators.transform(current, (element) -> pathLocateNext(element, path[index]))));
                } else {
                    return nextHead;
                }
            } else {
                return Iterators.emptyIterator();
            }
        } else {
            return current;
        }
    }

    /**
     * Evaluate the path at the current node and return a list of results.
     *
     * @param node       current node
     * @param path       specification of path to evaluate
     */
    private static Iterator<DataTreeNode> pathLocateNext(DataTreeNode node, ValueObject path) {
        if (path.getObjectType() == ValueObject.TYPE.CUSTOM &&
            path.asNative() == GLOB_OBJECT) {
            return node.getIterator();
        } else {
            return Iterators.singletonIterator(node.getNode(ValueUtil.asNativeString(path)));
        }
    }

}
