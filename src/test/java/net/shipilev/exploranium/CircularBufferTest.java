/*
 * #%L
 * Exploranium GR-100 Control
 * %%
 * Copyright (C) 2013 Aleksey Shipilev, and other contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package net.shipilev.exploranium;

import junit.framework.Assert;
import org.junit.Test;

import java.util.Arrays;

public class CircularBufferTest {

    @Test
    public void test1() {
        CircularBuffer<Integer> buffer = new CircularBuffer<Integer>(5);
        buffer.add(1);

        Assert.assertEquals(Arrays.asList(1), buffer.getAll());
    }

    @Test
    public void test2() {
        CircularBuffer<Integer> buffer = new CircularBuffer<Integer>(5);
        buffer.add(1);
        buffer.add(2);

        Assert.assertEquals(Arrays.asList(1, 2), buffer.getAll());
    }

    @Test
    public void test3() {
        CircularBuffer<Integer> buffer = new CircularBuffer<Integer>(5);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        buffer.add(4);
        buffer.add(5);
        buffer.add(6);

        Assert.assertEquals(Arrays.asList(2, 3, 4, 5, 6), buffer.getAll());
    }

    @Test
    public void test4() {
        CircularBuffer<Integer> buffer = new CircularBuffer<Integer>(5);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        buffer.add(4);
        buffer.add(5);
        buffer.add(6);
        buffer.add(7);
        buffer.add(8);
        buffer.add(9);

        Assert.assertEquals(Arrays.asList(5, 6, 7, 8, 9), buffer.getAll());
    }

}
