/*
 * See LICENSE file in distribution for copyright and licensing information.
 */
package org.yaml.snakeyaml.constructor;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.util.Base64Coder;

/**
 * @see PyYAML 3.06 for more information
 */
public class SafeConstructor extends BaseConstructor {

    public SafeConstructor() {
        this.yamlConstructors.put("tag:yaml.org,2002:null", new ConstuctYamlNull());
        this.yamlConstructors.put("tag:yaml.org,2002:bool", new ConstuctYamlBool());
        this.yamlConstructors.put("tag:yaml.org,2002:int", new ConstuctYamlInt());
        this.yamlConstructors.put("tag:yaml.org,2002:float", new ConstuctYamlFloat());
        this.yamlConstructors.put("tag:yaml.org,2002:binary", new ConstuctYamlBinary());
        this.yamlConstructors.put("tag:yaml.org,2002:timestamp", new ConstuctYamlTimestamp());
        this.yamlConstructors.put("tag:yaml.org,2002:omap", new ConstuctYamlOmap());
        this.yamlConstructors.put("tag:yaml.org,2002:pairs", new ConstuctYamlPairs());
        this.yamlConstructors.put("tag:yaml.org,2002:set", new ConstuctYamlSet());
        this.yamlConstructors.put("tag:yaml.org,2002:str", new ConstuctYamlStr());
        this.yamlConstructors.put("tag:yaml.org,2002:seq", new ConstuctYamlSeq());
        this.yamlConstructors.put("tag:yaml.org,2002:map", new ConstuctYamlMap());
        this.yamlConstructors.put(null, new ConstuctUndefined());
    }

    @SuppressWarnings("unchecked")
    protected Object constructScalar(Node node) {
        if (node instanceof MappingNode) {
            List<Node[]> nodeValue = (List<Node[]>) node.getValue();
            for (Iterator<Node[]> iter = nodeValue.iterator(); iter.hasNext();) {
                Node[] tuple = iter.next();
                Node keyNode = tuple[0];
                Node valueNode = tuple[1];
                if (keyNode.getTag().equals("tag:yaml.org,2002:value")) {
                    return constructScalar(valueNode);
                }
            }
        }
        return super.constructScalar((ScalarNode) node);
    }

    private void flattenMapping(MappingNode node) {
        List<Node[]> merge = new LinkedList<Node[]>();
        int index = 0;
        List<Node[]> nodeValue = (List<Node[]>) node.getValue();
        while (index < nodeValue.size()) {
            Node keyNode = nodeValue.get(index)[0];
            Node valueNode = nodeValue.get(index)[1];
            if (keyNode.getTag().equals("tag:yaml.org,2002:merge")) {
                nodeValue.remove(index);
                if (valueNode instanceof MappingNode) {
                    MappingNode mn = (MappingNode) valueNode;
                    flattenMapping(mn);
                    merge.addAll(mn.getValue());
                } else if (valueNode instanceof SequenceNode) {
                    List<List<Node[]>> submerge = new LinkedList<List<Node[]>>();
                    SequenceNode sn = (SequenceNode) valueNode;
                    List<Node> vals = sn.getValue();
                    for (Node subnode : vals) {
                        if (!(subnode instanceof MappingNode)) {
                            throw new ConstructorException("while constructing a mapping", node
                                    .getStartMark(), "expected a mapping for merging, but found "
                                    + subnode.getNodeId(), subnode.getStartMark());
                        }
                        MappingNode mn = (MappingNode) subnode;
                        flattenMapping(mn);
                        submerge.add(mn.getValue());
                    }
                    Collections.reverse(submerge);
                    for (List<Node[]> value : submerge) {
                        merge.addAll(value);
                    }
                } else {
                    throw new ConstructorException("while constructing a mapping", node
                            .getStartMark(),
                            "expected a mapping or list of mappings for merging, but found "
                                    + valueNode.getNodeId(), valueNode.getStartMark());
                }
            } else if (keyNode.getTag().equals("tag:yaml.org,2002:value")) {
                keyNode.setTag("tag:yaml.org,2002:str");
                index++;
            } else {
                index++;
            }
        }
        if (!merge.isEmpty()) {
            merge.addAll(nodeValue);
            ((MappingNode) node).setValue(merge);
        }
    }

    protected Map<Object, Object> constructMapping(MappingNode node) {
        flattenMapping(node);
        return super.constructMapping(node);
    }

    private class ConstuctYamlNull implements Construct {
        public Object construct(Node node) {
            constructScalar(node);
            return null;
        }
    }

    private final static Map<String, Boolean> BOOL_VALUES = new HashMap<String, Boolean>();
    static {
        BOOL_VALUES.put("yes", Boolean.TRUE);
        BOOL_VALUES.put("no", Boolean.FALSE);
        BOOL_VALUES.put("true", Boolean.TRUE);
        BOOL_VALUES.put("false", Boolean.FALSE);
        BOOL_VALUES.put("on", Boolean.TRUE);
        BOOL_VALUES.put("off", Boolean.FALSE);
    }

    private class ConstuctYamlBool implements Construct {
        public Object construct(Node node) {
            String val = (String) constructScalar(node);
            return BOOL_VALUES.get(val.toLowerCase());
        }
    }

    private class ConstuctYamlInt implements Construct {
        public Object construct(Node node) {
            String value = constructScalar(node).toString().replaceAll("_", "");
            int sign = +1;
            char first = value.charAt(0);
            if (first == '-') {
                sign = -1;
                value = value.substring(1);
            } else if (first == '+') {
                value = value.substring(1);
            }
            int base = 10;
            if (value.equals("0")) {
                return new Long(0);
            } else if (value.startsWith("0b")) {
                value = value.substring(2);
                base = 2;
            } else if (value.startsWith("0x")) {
                value = value.substring(2);
                base = 16;
            } else if (value.startsWith("0")) {
                value = value.substring(1);
                base = 8;
            } else if (value.indexOf(':') != -1) {
                final String[] digits = value.split(":");
                int bes = 1;
                int val = 0;
                for (int i = 0, j = digits.length; i < j; i++) {
                    val += (Long.parseLong(digits[(j - i) - 1]) * bes);
                    bes *= 60;
                }
                return new Long(sign * val);
            } else {
                return new Long(sign * Long.parseLong(value));
            }
            return new Long(sign * Long.parseLong(value, base));
        }
    }

    private class ConstuctYamlFloat implements Construct {
        public Object construct(Node node) {
            String value = constructScalar(node).toString().replaceAll("_", "");
            int sign = +1;
            char first = value.charAt(0);
            if (first == '-') {
                sign = -1;
                value = value.substring(1);
            } else if (first == '+') {
                value = value.substring(1);
            }
            final String valLower = value.toLowerCase();
            if (valLower.equals(".inf")) {
                return sign == -1 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            } else if (valLower.equals(".nan")) {
                return Double.NaN;
            } else if (value.indexOf(':') != -1) {
                final String[] digits = value.split(":");
                int bes = 1;
                double val = 0.0;
                for (int i = 0, j = digits.length; i < j; i++) {
                    val += (Double.parseDouble(digits[(j - i) - 1]) * bes);
                    bes *= 60;
                }
                return new Double(sign * val);
            } else {
                try {
                    Double d = Double.valueOf(value);
                    return new Double(d.doubleValue() * sign);
                } catch (NumberFormatException e) {
                    throw new YAMLException("Invalid number: '" + value + "'; in node " + node);
                }
            }
        }
    }

    private class ConstuctYamlBinary implements Construct {
        public Object construct(Node node) {
            char[] decoded = Base64Coder.decode(constructScalar(node).toString().toCharArray());
            String value = new String(decoded);
            return value;
        }
    }

    private final static Pattern TIMESTAMP_REGEXP = Pattern
            .compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\\.([0-9]*))?(?:[ \t]*(?:Z|([-+][0-9][0-9]?)(?::([0-9][0-9])?)?))?)?$");
    private final static Pattern YMD_REGEXP = Pattern
            .compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)$");

    private class ConstuctYamlTimestamp implements Construct {
        public Object construct(Node node) {
            Matcher match = YMD_REGEXP.matcher((String) node.getValue());
            if (match.matches()) {
                final String year_s = match.group(1);
                final String month_s = match.group(2);
                final String day_s = match.group(3);
                final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.clear();
                if (year_s != null) {
                    cal.set(Calendar.YEAR, Integer.parseInt(year_s));
                }
                if (month_s != null) {
                    cal.set(Calendar.MONTH, Integer.parseInt(month_s) - 1); // Java's
                    // months
                    // are
                    // zero-based...
                }
                if (day_s != null) {
                    cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day_s));
                }
                return cal.getTime();
            }
            match = TIMESTAMP_REGEXP.matcher((String) node.getValue());
            if (!match.matches()) {
                throw new YAMLException("Expected timestamp: " + node);
            }
            final String year_s = match.group(1);
            final String month_s = match.group(2);
            final String day_s = match.group(3);
            final String hour_s = match.group(4);
            final String min_s = match.group(5);
            final String sec_s = match.group(6);
            final String fract_s = match.group(7);
            final String timezoneh_s = match.group(8);
            final String timezonem_s = match.group(9);

            int usec = 0;
            if (fract_s != null) {
                usec = Integer.parseInt(fract_s);
                if (usec != 0) {
                    while (10 * usec < 1000) {
                        usec *= 10;
                    }
                }
            }
            final Calendar cal = Calendar.getInstance();
            if (year_s != null) {
                cal.set(Calendar.YEAR, Integer.parseInt(year_s));
            }
            if (month_s != null) {
                cal.set(Calendar.MONTH, Integer.parseInt(month_s) - 1); // Java's
                // months
                // are
                // zero-based...
            }
            if (day_s != null) {
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day_s));
            }
            if (hour_s != null) {
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour_s));
            }
            if (min_s != null) {
                cal.set(Calendar.MINUTE, Integer.parseInt(min_s));
            }
            if (sec_s != null) {
                cal.set(Calendar.SECOND, Integer.parseInt(sec_s));
            }
            cal.set(Calendar.MILLISECOND, usec);
            if (timezoneh_s != null || timezonem_s != null) {
                int zone = 0;
                int sign = +1;
                if (timezoneh_s != null) {
                    if (timezoneh_s.startsWith("-")) {
                        sign = -1;
                    }
                    zone += Integer.parseInt(timezoneh_s.substring(1)) * 3600000;
                }
                if (timezonem_s != null) {
                    zone += Integer.parseInt(timezonem_s) * 60000;
                }
                cal.set(Calendar.ZONE_OFFSET, sign * zone);
            } else {
                cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            return cal.getTime();
        }
    }

    private class ConstuctYamlOmap implements Construct {
        public Object construct(Node node) {
            // Note: we do not check for duplicate keys, because it's too
            // CPU-expensive.
            SortedMap<Object, Object> omap = new TreeMap<Object, Object>();
            if (!(node instanceof SequenceNode)) {
                throw new ConstructorException("while constructing an ordered map", node
                        .getStartMark(), "expected a sequence, but found " + node.getNodeId(), node
                        .getStartMark());
            }
            SequenceNode snode = (SequenceNode) node;
            for (Node subnode : snode.getValue()) {
                if (!(node instanceof MappingNode)) {
                    throw new ConstructorException("while constructing an ordered map", node
                            .getStartMark(), "expected a mapping of length 1, but found "
                            + node.getNodeId(), node.getStartMark());
                }
                MappingNode mnode = (MappingNode) subnode;
                if (mnode.getValue().size() != 1) {
                    throw new ConstructorException("while constructing an ordered map", node
                            .getStartMark(), "expected a single mapping item, but found "
                            + mnode.getValue().size() + " items", mnode.getStartMark());
                }
                Node keyNode = mnode.getValue().get(0)[0];
                Node valueNode = mnode.getValue().get(0)[1];
                Object key = constructObject(keyNode);
                Object value = constructObject(valueNode);
                omap.put(key, value);
            }
            return omap;
        }
    }

    // Note: the same code as `construct_yaml_omap`.
    private class ConstuctYamlPairs implements Construct {
        public Object construct(Node node) {
            // Note: we do not check for duplicate keys, because it's too
            // CPU-expensive.
            List<Object[]> pairs = new LinkedList<Object[]>();
            if (!(node instanceof SequenceNode)) {
                throw new ConstructorException("while constructing pairs", node.getStartMark(),
                        "expected a sequence, but found " + node.getNodeId(), node.getStartMark());
            }
            SequenceNode snode = (SequenceNode) node;
            for (Node subnode : snode.getValue()) {
                if (!(node instanceof MappingNode)) {
                    throw new ConstructorException("while constructingpairs", node.getStartMark(),
                            "expected a mapping of length 1, but found " + node.getNodeId(), node
                                    .getStartMark());
                }
                MappingNode mnode = (MappingNode) subnode;
                if (mnode.getValue().size() != 1) {
                    throw new ConstructorException("while constructing pairs", node.getStartMark(),
                            "expected a single mapping item, but found " + mnode.getValue().size()
                                    + " items", mnode.getStartMark());
                }
                Node keyNode = mnode.getValue().get(0)[0];
                Node valueNode = mnode.getValue().get(0)[1];
                Object key = constructObject(keyNode);
                Object value = constructObject(valueNode);
                pairs.add(new Object[] { key, value });
            }
            return pairs;
        }
    }

    private class ConstuctYamlSet implements Construct {
        public Object construct(Node node) {
            Map<Object, Object> value = constructMapping((MappingNode) node);
            return value.keySet();
        }
    }

    private class ConstuctYamlStr implements Construct {
        public Object construct(Node node) {
            String value = (String) constructScalar((ScalarNode) node);
            return value;
        }
    }

    private class ConstuctYamlSeq implements Construct {
        public Object construct(Node node) {
            return constructSequence((SequenceNode) node);
        }
    }

    private class ConstuctYamlMap implements Construct {
        public Object construct(Node node) {
            return constructMapping((MappingNode) node);
        }
    }

    private class ConstuctUndefined implements Construct {
        public Object construct(Node node) {
            throw new ConstructorException(null, null,
                    "could not determine a constructor for the tag " + node.getTag(), node
                            .getStartMark());
        }
    }
}