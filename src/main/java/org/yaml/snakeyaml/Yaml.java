/*
 * See LICENSE file in distribution for copyright and licensing information.
 */
package org.yaml.snakeyaml;

import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Public YAML interface
 */
public class Yaml {
    private Dumper dumper;
    private Loader loader;

    public Yaml(DumperOptions options) {
        this.dumper = new Dumper(options);
    }

    public Yaml(Dumper dumper) {
        this(new Loader(), dumper);
    }

    public Yaml(Loader loader) {
        this(loader, new Dumper(new DumperOptions()));
    }

    public Yaml(Loader loader, Dumper dumper) {
        this.loader = loader;
        this.dumper = dumper;
    }

    public Yaml() {
        this(new Loader(), new Dumper(new DumperOptions()));
    }

    /**
     * Serialize a Java object into a YAML String.
     * 
     * @param data
     *            - Java object to be Serialized to YAML
     * @return YAML String
     */
    public String dump(final Object data) {
        List<Object> lst = new ArrayList<Object>(1);
        lst.add(data);
        return dumpAll(lst);
    }

    /**
     * Serialize a sequence of Java objects into a YAML String.
     * 
     * @param data
     *            - Iterator with Objects
     * @return - YAML String with all the objects in proper sequence
     */
    public String dumpAll(final Iterable<Object> data) {
        StringWriter buffer = new StringWriter();
        dumpAll(data, buffer);
        return buffer.toString();
    }

    /**
     * Serialize a Java object into a YAML stream.
     * 
     * @param data
     *            - Java object to be Serialized to YAML
     * @param output
     *            - stream to write to
     */
    public void dump(final Object data, final Writer output) {
        List<Object> lst = new ArrayList<Object>(1);
        lst.add(data);
        dumpAll(lst, output);
    }

    /**
     * Serialize a sequence of Java objects into a YAML stream.
     * 
     * @param data
     *            - Iterator with Objects
     * @param output
     *            - stream to write to
     */
    public void dumpAll(final Iterable<Object> data, final Writer output) {
        dumper.dump(data, output);
    }

    /**
     * Parse the first YAML document in a String and produce the corresponding
     * Java object. (Because the encoding in known BOM is not respected.)
     * 
     * @param yaml
     *            - YAML data to load from (BOM must not be present)
     * @return parsed object
     */
    public Object load(String yaml) {
        return loader.load(yaml);
    }

    /**
     * Parse the first YAML document in a stream and produce the corresponding
     * Java object.
     * 
     * @param io
     *            - data to load from (BOM is respected and ignored)
     * @return parsed object
     */
    public Object load(InputStream io) {
        return loader.load(io);
    }

    /**
     * Parse all YAML documents in a String and produce corresponding Java
     * objects. (Because the encoding in known BOM is not respected.)
     * 
     * @param yaml
     *            - YAML data to load from (BOM must not be present)
     * @return an iterator over the parsed Java objects in this String in proper
     *         sequence
     */
    public Iterable<Object> loadAll(String yaml) {
        return loader.loadAll(yaml);
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding Java
     * objects.
     * 
     * @param yaml
     *            - YAML data to load from (BOM is respected and ignored)
     * @return an iterator over the parsed Java objects in this stream in proper
     *         sequence
     */
    public Iterable<Object> loadAll(InputStream yaml) {
        return loader.loadAll(yaml);
    }
}
