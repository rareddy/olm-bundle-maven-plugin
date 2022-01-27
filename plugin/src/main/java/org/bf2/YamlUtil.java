/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
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
 */
package org.bf2;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

public class YamlUtil {
    public static <K, V> Map<K,V> sortedMap(K k1, V v1, K k2, V v2){
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static void write(Map<?, ?> fragment, File out) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml(options);
        FileWriter fw = new FileWriter(out, Charset.forName("UTF-8"));
        yaml.dump(fragment, fw);
        fw.close();
    }

    public static <T> T find(Map<?, ?> fragment, String prop, Class<T> clazz) {
        String[] propertyFragments = prop.split("\\^");
        Map<?, ?> map = fragment;
        for (int i = 0; i < propertyFragments.length; i++) {
            Object value = map.get(propertyFragments[i]);
            if (i == propertyFragments.length-1) {
                return clazz.cast(value);
            }
            map = (Map<?, ?>)value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Object put(Map<Object, Object> fragment, String prop, Object propValue) {
        String[] propertyFragments = prop.split("\\^");
        Map<Object, Object> map = fragment;
        for (int i = 0; i < propertyFragments.length; i++) {
            Object value = map.get(propertyFragments[i]);
            if (i < propertyFragments.length-1 && value == null) {
                value = new LinkedHashMap<>();
                map.put(propertyFragments[i], value);
            }
            if (i == propertyFragments.length-1) {
                return map.put(propertyFragments[i], propValue);
            } else {
                map = (Map<Object, Object>)value;
            }
        }
        return null;
    }

    public static void delete(Map<?, ?> fragment, String prop) {
        String[] propertyFragments = prop.split("\\^");
        Map<?, ?> map = fragment;
        for (int i = 0; i < propertyFragments.length; i++) {
            Object value = map.get(propertyFragments[i]);
            if (i == propertyFragments.length-1) {
                map.remove(propertyFragments[i]);
                break;
            }
            map = (Map<?, ?>)value;
        }
    }
}
