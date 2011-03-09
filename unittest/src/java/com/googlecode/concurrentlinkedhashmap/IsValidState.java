/*
 * Copyright 2011 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import com.google.common.collect.Sets;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A matcher that evaluates a {@link ConcurrentLinkedHashMap} to determine if it
 * is in a valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class IsValidState extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("state");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    drain(map);
    checkMap(map, builder);
    checkEvictionDeque(map, builder);
    return builder.matches();
  }

  private void drain(ConcurrentLinkedHashMap<?, ?> map) {
    for (;;) {
      map.tryToDrainEvictionQueues(false);

      int pending = 0;
      for (int i = 0; i < map.bufferLength.length(); i++) {
        pending += map.bufferLength.get(i);
      }
      if (pending == 0) {
        break;
      }
    }
  }

  private void checkMap(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    for (int i = 0; i < map.buffers.length; i++) {
      builder.expect(map.buffers[i].isEmpty(), "recencyQueue not empty");
      builder.expect(map.bufferLength.get(i) == 0, "recencyQueueLength != 0");
    }
    builder.expect(map.pendingNotifications.isEmpty(), "listenerQueue");
    builder.expectEqual(map.data.size(), map.size(), "Inconsistent size");
    builder.expectEqual(map.weightedSize(), map.weightedSize, "weightedSize");
    builder.expectEqual(map.capacity(), map.capacity, "capacity");
    builder.expect(map.capacity >= map.weightedSize(), "overflow");
    builder.expectNot(((ReentrantLock) map.evictionLock).isLocked());

    boolean empty = new IsEmptyMap().matchesSafely(map, builder.getDescription());
    if (map.isEmpty()) {
      builder.expect(empty);
    } else {
      builder.expectNot(empty);
    }
  }

  private void checkEvictionDeque(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    LinkedDeque<?> deque = map.evictionDeque;

    checkLinks(map, builder);
    builder.expectEqual(deque.size(), map.size());
  }

  private void checkLinks(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    int weightedSize = 0;
    Set<Node> seen = Sets.newIdentityHashSet();
    for (Node node : map.evictionDeque) {
      builder.expect(seen.add(node), "Loop detected: %s, saw %s in %s", node, seen, map);
      weightedSize += node.getWeightedValue().weight;
      checkNode(map, node, builder);
    }

    builder.expectEqual(map.size(), seen.size(), "Size != list length");
    builder.expectEqual(map.weightedSize(), weightedSize, "WeightedSize != link weights"
        + " [" + map.weightedSize() + " vs. " + weightedSize + "]"
        + " {size: " + map.size() + " vs. " + seen.size() + "}");
  }

  private void checkNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
      DescriptionBuilder builder) {
    builder.expectNotEqual(node.key, null, "null key");
    builder.expectNotEqual(node.getWeightedValue(), null, "null weighted value");
    builder.expectNotEqual(node.getWeightedValue().value, null, "null value");
    builder.expectEqual(node.getWeightedValue().weight,
      ((Weigher) map.weigher).weightOf(node.getWeightedValue().value), "weight");

    builder.expect(map.containsKey(node.key), "inconsistent");
    builder.expect(map.containsValue(node.getWeightedValue().value),
        "Could not find value: %s", node.getWeightedValue().value);
    builder.expectEqual(map.data.get(node.key), node, "found wrong node");

    Node first = map.evictionDeque.peekFirst();
    Node last = map.evictionDeque.peekLast();
    if (node == first) {
      builder.expectEqual(node.prev, null, "not null prev");
    }
    if (node == last) {
      builder.expectEqual(node.next, null, "not null next");
    }
    if ((node != first) && (node != last)) {
      builder.expectNotEqual(node.prev, null, "null prev");
      builder.expectNotEqual(node.next, null, "null next");
    }
  }

  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> valid() {
    return new IsValidState();
  }
}
