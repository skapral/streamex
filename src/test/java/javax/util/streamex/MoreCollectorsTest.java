/*
 * Copyright 2015 Tagir Valeev
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
package javax.util.streamex;

import static org.junit.Assert.*;
import static javax.util.streamex.TestHelpers.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class MoreCollectorsTest {
    @Test(expected = UnsupportedOperationException.class)
    public void testInstantiate() throws Throwable {
        Constructor<MoreCollectors> constructor = MoreCollectors.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testToArray() {
        List<String> input = Arrays.asList("a", "bb", "c", "", "cc", "eee", "bb", "ddd");
        for (StreamSupplier<String, StreamEx<String>> supplier : suppliers(() -> StreamEx.of(input))) {
            Map<Integer, String[]> result = supplier.get().groupingBy(String::length, HashMap::new,
                MoreCollectors.toArray(String[]::new));
            assertArrayEquals(supplier.toString(), new String[] { "" }, result.get(0));
            assertArrayEquals(supplier.toString(), new String[] { "a", "c" }, result.get(1));
            assertArrayEquals(supplier.toString(), new String[] { "bb", "cc", "bb" }, result.get(2));
            assertArrayEquals(supplier.toString(), new String[] { "eee", "ddd" }, result.get(3));
        }
    }

    @Test
    public void testDistinctCount() {
        List<String> input = Arrays.asList("a", "bb", "c", "cc", "eee", "bb", "bc", "ddd");
        for (StreamExSupplier<String> supplier : streamEx(input::stream)) {
            Map<String, Integer> result = supplier.get().groupingBy(s -> s.substring(0, 1), HashMap::new,
                MoreCollectors.distinctCount(String::length));
            assertEquals(1, (int) result.get("a"));
            assertEquals(1, (int) result.get("b"));
            assertEquals(2, (int) result.get("c"));
            assertEquals(1, (int) result.get("d"));
            assertEquals(1, (int) result.get("e"));
        }
    }
    
    @Test
    public void testDistinctBy() {
        List<String> input = Arrays.asList("a", "bb", "c", "cc", "eee", "bb", "bc", "ddd", "ca", "ce", "cf", "ded", "dump");
        for (StreamExSupplier<String> supplier : streamEx(input::stream)) {
            Map<String, List<String>> result = supplier.get().groupingBy(s -> s.substring(0, 1), HashMap::new,
                MoreCollectors.distinctBy(String::length));
            assertEquals(supplier.toString(), Arrays.asList("a"), result.get("a"));
            assertEquals(supplier.toString(), Arrays.asList("bb"), result.get("b"));
            assertEquals(supplier.toString(), Arrays.asList("c", "cc"), result.get("c"));
            assertEquals(supplier.toString(), Arrays.asList("ddd", "dump"), result.get("d"));
            assertEquals(supplier.toString(), Arrays.asList("eee"), result.get("e"));
        }
    }

    @Test
    public void testMaxAll() {
        List<String> input = Arrays.asList("a", "bb", "c", "", "cc", "eee", "bb", "ddd");
        for (StreamExSupplier<String> supplier : streamEx(input::stream)) {
            assertEquals(supplier.toString(), Arrays.asList("eee", "ddd"),
                supplier.get().collect(MoreCollectors.maxAll(Comparator.comparingInt(String::length))));
            assertEquals(
                supplier.toString(),
                1L,
                (long) supplier.get().collect(
                    MoreCollectors.minAll(Comparator.comparingInt(String::length), Collectors.counting())));
            assertEquals(
                supplier.toString(),
                "eee,ddd",
                supplier.get().collect(
                    MoreCollectors.maxAll(Comparator.comparingInt(String::length), Collectors.joining(","))));
            assertEquals(supplier.toString(), Arrays.asList(""),
                supplier.get().collect(MoreCollectors.minAll(Comparator.comparingInt(String::length))));
        }
        assertEquals(Collections.emptyList(),
            StreamEx.<String> empty().collect(MoreCollectors.maxAll(Comparator.comparingInt(String::length))));
        assertEquals(Collections.emptyList(),
            StreamEx.<String> empty().parallel()
                    .collect(MoreCollectors.maxAll(Comparator.comparingInt(String::length))));

        List<Integer> ints = IntStreamEx.of(new Random(1), 10000, 1, 1000).boxed().toList();
        List<Integer> expectedMax = null;
        List<Integer> expectedMin = null;
        for (Integer i : ints) {
            if (expectedMax == null || i > expectedMax.get(0)) {
                expectedMax = new ArrayList<>();
                expectedMax.add(i);
            } else if (i.equals(expectedMax.get(0))) {
                expectedMax.add(i);
            }
            if (expectedMin == null || i < expectedMin.get(0)) {
                expectedMin = new ArrayList<>();
                expectedMin.add(i);
            } else if (i.equals(expectedMin.get(0))) {
                expectedMin.add(i);
            }
        }
        Collector<Integer, ?, SimpleEntry<Integer, Long>> downstream = MoreCollectors.pairing(MoreCollectors.first(),
            Collectors.counting(), (opt, cnt) -> new AbstractMap.SimpleEntry<>(opt.get(), cnt));

        for (StreamExSupplier<Integer> supplier : streamEx(ints::stream)) {
            assertEquals(supplier.toString(), expectedMax,
                supplier.get().collect(MoreCollectors.maxAll(Integer::compare)));

            SimpleEntry<Integer, Long> entry = supplier.get().collect(MoreCollectors.maxAll(downstream));
            assertEquals(supplier.toString(), expectedMax.size(), (long) entry.getValue());
            assertEquals(supplier.toString(), expectedMax.get(0), entry.getKey());

            assertEquals(supplier.toString(), expectedMin,
                supplier.get().collect(MoreCollectors.minAll(Integer::compare)));

            entry = supplier.get().collect(MoreCollectors.minAll(downstream));
            assertEquals(supplier.toString(), expectedMin.size(), (long) entry.getValue());
            assertEquals(supplier.toString(), expectedMin.get(0), entry.getKey());
        }
        
        Integer a = new Integer(1), b = new Integer(1), c = new Integer(1000), d = new Integer(1000);
        ints = IntStreamEx.range(10, 100).boxed().append(a, c).prepend(b, d).toList();
        for (StreamExSupplier<Integer> supplier : streamEx(ints::stream)) {
            List<Integer> list = supplier.get().collect(MoreCollectors.maxAll());
            assertEquals(2, list.size());
            assertSame(d, list.get(0));
            assertSame(c, list.get(1));
            
            list = supplier.get().collect(MoreCollectors.minAll());
            assertEquals(2, list.size());
            assertSame(b, list.get(0));
            assertSame(a, list.get(1));
        }
    }

    @Test
    public void testFirstLast() {
        for (StreamExSupplier<Integer> supplier : streamEx(() -> IntStreamEx.range(1000).boxed())) {
            assertEquals(supplier.toString(), 999, (int) supplier.get().collect(MoreCollectors.last()).get());
            assertEquals(supplier.toString(), 0, (int) supplier.get().collect(MoreCollectors.first()).orElse(-1));
        }
        for (StreamExSupplier<Integer> supplier : emptyStreamEx(Integer.class)) {
            assertFalse(supplier.toString(), supplier.get().collect(MoreCollectors.first()).isPresent());
            assertFalse(supplier.toString(), supplier.get().collect(MoreCollectors.last()).isPresent());
        }
        assertEquals(1, (int)StreamEx.iterate(1, x -> x+1).parallel().collect(MoreCollectors.first()).get());
    }
    
    @Test
    public void testHeadTailParallel() {
        for(int i=0; i<100; i++) {
            assertEquals("#"+i, Arrays.asList(0, 1), IntStreamEx.range(1000).boxed().parallel().collect(MoreCollectors.head(2)));
        }
        assertEquals(Arrays.asList(0, 1), StreamEx.iterate(0, x -> x+1).parallel().collect(MoreCollectors.head(2)));
    }

    @Test
    public void testHeadTail() {
        for (StreamExSupplier<Integer> supplier : streamEx(() -> IntStreamEx.range(1000).boxed())) {
            assertEquals(supplier.toString(), Arrays.asList(), supplier.get().collect(MoreCollectors.tail(0)));
            assertEquals(supplier.toString(), Arrays.asList(999), supplier.get().collect(MoreCollectors.tail(1)));
            assertEquals(supplier.toString(), Arrays.asList(998, 999), supplier.get().collect(MoreCollectors.tail(2)));
            assertEquals(supplier.toString(), supplier.get().skip(1).toList(),
                supplier.get().collect(MoreCollectors.tail(999)));
            assertEquals(supplier.toString(), supplier.get().toList(), supplier.get()
                    .collect(MoreCollectors.tail(1000)));
            assertEquals(supplier.toString(), supplier.get().toList(),
                supplier.get().collect(MoreCollectors.tail(Integer.MAX_VALUE)));

            assertEquals(supplier.toString(), Arrays.asList(), supplier.get().collect(MoreCollectors.head(0)));
            assertEquals(supplier.toString(), Arrays.asList(0), supplier.get().collect(MoreCollectors.head(1)));
            assertEquals(supplier.toString(), Arrays.asList(0, 1), supplier.get().collect(MoreCollectors.head(2)));
            assertEquals(supplier.toString(), supplier.get().limit(999).toList(),
                supplier.get().collect(MoreCollectors.head(999)));
            assertEquals(supplier.toString(), supplier.get().toList(), supplier.get()
                    .collect(MoreCollectors.head(1000)));
            assertEquals(supplier.toString(), supplier.get().toList(),
                supplier.get().collect(MoreCollectors.head(Integer.MAX_VALUE)));
        }
    }

    @Test
    public void testGreatest() {
        List<Integer> ints = IntStreamEx.of(new Random(1), 1000, 1, 1000).boxed().toList();
        Comparator<Integer> byString = Comparator.comparing(String::valueOf);
        for (StreamExSupplier<Integer> supplier : streamEx(ints::stream)) {
            assertEquals(supplier.toString(), Collections.emptyList(), supplier.get().collect(MoreCollectors.least(0)));
            assertEquals(supplier.toString(), supplier.get().sorted().limit(5).toList(),
                supplier.get().collect(MoreCollectors.least(5)));
            assertEquals(supplier.toString(), supplier.get().sorted().limit(20).toList(),
                supplier.get().collect(MoreCollectors.least(20)));
            assertEquals(supplier.toString(), supplier.get().sorted(byString).limit(20).toList(), supplier.get()
                    .collect(MoreCollectors.least(byString, 20)));
            assertEquals(supplier.toString(), supplier.get().sorted().toList(),
                supplier.get().collect(MoreCollectors.least(Integer.MAX_VALUE)));

            assertEquals(supplier.toString(), Collections.emptyList(),
                supplier.get().collect(MoreCollectors.greatest(0)));
            assertEquals(supplier.toString(), supplier.get().reverseSorted().limit(5).toList(),
                supplier.get().collect(MoreCollectors.greatest(5)));
            assertEquals(supplier.toString(), supplier.get().reverseSorted().limit(20).toList(), supplier.get()
                    .collect(MoreCollectors.greatest(20)));
            assertEquals(supplier.toString(), supplier.get().reverseSorted(byString).limit(20).toList(), supplier.get()
                    .collect(MoreCollectors.greatest(byString, 20)));
            assertEquals(supplier.toString(), supplier.get().reverseSorted().toList(),
                supplier.get().collect(MoreCollectors.greatest(Integer.MAX_VALUE)));
        }

        for (StreamExSupplier<Integer> supplier : streamEx(() -> IntStreamEx.range(100).boxed())) {
            assertEquals(supplier.toString(), IntStreamEx.range(1).boxed().toList(),
                supplier.get().collect(MoreCollectors.least(1)));
            assertEquals(supplier.toString(), IntStreamEx.range(2).boxed().toList(),
                supplier.get().collect(MoreCollectors.least(2)));
            assertEquals(supplier.toString(), IntStreamEx.range(10).boxed().toList(),
                supplier.get().collect(MoreCollectors.least(10)));
            assertEquals(supplier.toString(), IntStreamEx.range(100).boxed().toList(),
                supplier.get().collect(MoreCollectors.least(100)));
            assertEquals(supplier.toString(), IntStreamEx.range(100).boxed().toList(),
                supplier.get().collect(MoreCollectors.least(200)));
        }
    }

    @Test
    public void testCountingInt() {
        for (StreamExSupplier<Integer> supplier : streamEx(() -> IntStreamEx.range(1000).boxed())) {
            assertEquals(supplier.toString(), 1000, (int) supplier.get().collect(MoreCollectors.countingInt()));
        }
    }

    @Test
    public void testMinIndex() {
        List<Integer> ints = IntStreamEx.of(new Random(1), 1000, 0, 100).boxed().toList();
        long expectedMin = IntStreamEx.ofIndices(ints).minBy(ints::get).getAsInt();
        long expectedMax = IntStreamEx.ofIndices(ints).maxBy(ints::get).getAsInt();
        long expectedMinString = IntStreamEx.ofIndices(ints).minBy(i -> String.valueOf(ints.get(i))).getAsInt();
        long expectedMaxString = IntStreamEx.ofIndices(ints).maxBy(i -> String.valueOf(ints.get(i))).getAsInt();
        for (StreamExSupplier<Integer> supplier : streamEx(ints::stream)) {
            assertEquals(supplier.toString(), expectedMin, supplier.get().collect(MoreCollectors.minIndex())
                    .getAsLong());
            assertEquals(supplier.toString(), expectedMax, supplier.get().collect(MoreCollectors.maxIndex())
                    .getAsLong());
            assertEquals(supplier.toString(), expectedMinString,
                supplier.get().collect(MoreCollectors.minIndex(Comparator.comparing(String::valueOf))).getAsLong());
            assertEquals(supplier.toString(), expectedMaxString,
                supplier.get().collect(MoreCollectors.maxIndex(Comparator.comparing(String::valueOf))).getAsLong());
            assertEquals(supplier.toString(), expectedMinString,
                supplier.get().map(String::valueOf).collect(MoreCollectors.minIndex()).getAsLong());
            assertEquals(supplier.toString(), expectedMaxString,
                supplier.get().map(String::valueOf).collect(MoreCollectors.maxIndex()).getAsLong());
        }
        for (StreamExSupplier<Integer> supplier : emptyStreamEx(Integer.class)) {
            assertFalse(supplier.toString(), supplier.get().collect(MoreCollectors.minIndex()).isPresent());
            assertFalse(supplier.toString(), supplier.get().collect(MoreCollectors.maxIndex()).isPresent());
        }
    }
    
    @Test
    public void testGroupingByEnum() {
        for(StreamExSupplier<TimeUnit> supplier:streamEx(() -> Stream.of(TimeUnit.SECONDS, TimeUnit.DAYS, TimeUnit.DAYS, TimeUnit.NANOSECONDS))) {
            EnumMap<TimeUnit, Long> map = supplier.get().collect(
                MoreCollectors.groupingByEnum(TimeUnit.class, Function.identity(), Collectors.counting()));
            assertEquals(supplier.toString(), 1L, (long)map.get(TimeUnit.SECONDS));
            assertEquals(supplier.toString(), 2L, (long)map.get(TimeUnit.DAYS));
            assertEquals(supplier.toString(), 1L, (long)map.get(TimeUnit.NANOSECONDS));
            assertEquals(supplier.toString(), 0L, (long)map.get(TimeUnit.MICROSECONDS));
        }
    }
    
    @Test
    public void testToBooleanArray() {
        List<Integer> input = IntStreamEx.of(new Random(1), 1000, 1, 100).boxed().toList();
        boolean[] expected = new boolean[input.size()];
        for(int i=0; i<expected.length; i++)
            expected[i] = input.get(i) > 50;
        for(StreamExSupplier<Integer> supplier : streamEx(input::stream)) {
            assertArrayEquals(supplier.toString(), expected,
                supplier.get().collect(MoreCollectors.toBooleanArray(x -> x > 50)));
        }
    }
    
    @Test
    public void testPartitioningBy() {
        AtomicInteger counter = new AtomicInteger();
        Map<Boolean, Optional<Integer>> map = IntStreamEx.range(1, 100).boxed()
                .peek(x -> counter.incrementAndGet())
                .collect(MoreCollectors.partitioningBy(x -> x % 20 == 0, MoreCollectors.first()));
        assertEquals(20, (int)map.get(true).get());
        assertEquals(1, (int)map.get(false).get());
        assertEquals(20, counter.get()); // short-circuit

        counter.set(0);
        map = IntStreamEx.range(1, 100).boxed()
                .peek(x -> counter.incrementAndGet())
                .collect(MoreCollectors.partitioningBy(x -> x % 200 == 0, MoreCollectors.first()));
        assertFalse(map.get(true).isPresent());
        assertEquals(1, (int)map.get(false).get());
        assertEquals(99, counter.get());
    }
    
    @Test
    public void testMapping() {
        List<String> input = Arrays.asList("Capital", "lower", "Foo", "bar");
        Collector<String, ?, Map<Boolean, Optional<Integer>>> collector = MoreCollectors
                .partitioningBy(str -> Character.isUpperCase(str.charAt(0)),
                    MoreCollectors.mapping(String::length, MoreCollectors.first()));
        AtomicInteger counter = new AtomicInteger();
        StreamEx.of(input).peek(x -> counter.incrementAndGet()).collect(collector);
        assertEquals(2, counter.get());
        for (StreamExSupplier<String> supplier : streamEx(input::stream)) {
            Map<Boolean, Optional<Integer>> map = supplier.get().collect(collector);
            assertEquals(7, (int) map.get(true).get());
            assertEquals(5, (int) map.get(false).get());
            map = supplier.get().collect(Collectors.collectingAndThen(collector, Function.identity()));
            assertEquals(7, (int) map.get(true).get());
            assertEquals(5, (int) map.get(false).get());
        }
        Collector<String, ?, Map<Boolean, Optional<Integer>>> collectorLast = MoreCollectors
                .partitioningBy(str -> Character.isUpperCase(str.charAt(0)),
                    MoreCollectors.mapping(String::length, MoreCollectors.last()));
        for (StreamExSupplier<String> supplier : streamEx(input::stream)) {
            Map<Boolean, Optional<Integer>> map = supplier.get().collect(collectorLast);
            assertEquals(3, (int) map.get(true).get());
            assertEquals(3, (int) map.get(false).get());
        }
    }
    
    @Test
    public void testIntersecting() {
        List<List<String>> input = Arrays.asList(Arrays.asList("aa", "bb", "cc"), Arrays.asList("cc", "bb", "dd"),
            Arrays.asList("ee", "dd"), Arrays.asList("aa", "bb", "dd"));
        AtomicInteger counter = new AtomicInteger();
        StreamEx.of(input).peek(t -> counter.incrementAndGet()).collect(MoreCollectors.intersecting());
        assertEquals(3, counter.get());
        for(StreamExSupplier<List<String>> supplier : streamEx(input::stream)) {
            assertEquals(supplier.toString(), Collections.emptySet(),
                supplier.get().collect(MoreCollectors.intersecting()));
        }
        List<List<Integer>> copies = Collections.nCopies(100, Arrays.asList(1, 2));
        for(StreamExSupplier<List<Integer>> supplier : streamEx(copies::stream)) {
            assertEquals(supplier.toString(), StreamEx.of(1, 2).toSet(),
                supplier.get().collect(MoreCollectors.intersecting()));
        }
        for(StreamExSupplier<List<Integer>> supplier : streamEx(() -> StreamEx.of(copies).prepend(Stream.of(Arrays.asList(3))))) {
            assertEquals(supplier.toString(), Collections.emptySet(),
                supplier.get().collect(MoreCollectors.intersecting()));
        }
        assertEquals(Collections.emptySet(), StreamEx.<List<Integer>>empty().collect(MoreCollectors.intersecting()));
    }
    
    @Test
    public void testJoining() {
        List<String> input = Arrays.asList("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten");
        String expected = "one, two, three, four, five, six, seven, eight, nine, ten";
        for(int i=3; i<expected.length()+5; i++) {
            String exp = expected;
            if(exp.length() > i) {
                exp = exp.substring(0, i-3)+"...";
            }
            for(StreamExSupplier<String> supplier : streamEx(input::stream)) {
                assertEquals(supplier+"/#"+i, exp, supplier.get().collect(MoreCollectors.joining(", ", "...", i)));
            }
        }
    }
}
