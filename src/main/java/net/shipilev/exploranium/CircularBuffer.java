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

import java.util.ArrayList;
import java.util.Collection;

public class CircularBuffer<T> {

    private final T[] objects;
    private final int size;
    private int index;
    private int count;

    public CircularBuffer(int size) {
        this.objects = (T[]) new Object[size];
        this.size = size;
        index = -1;
    }

    public void add(T obj) {
        index = (index + 1) % size;
        count = Math.max(index + 1, count);
        objects[index] = obj;
    }

    public Collection<T> getAll() {
        Collection<T> result = new ArrayList<T>(count);
        int n = index + 1;
        if (n >= count) {
            for (int i = 0; i < count; i++) {
                result.add(objects[i]);
            }
        } else {
            for (int i = n; i < count; i++) {
                result.add(objects[i]);
            }
            for (int i = 0; i < n; i++) {
                result.add(objects[i]);
            }
        }
        return result;
    }


}
