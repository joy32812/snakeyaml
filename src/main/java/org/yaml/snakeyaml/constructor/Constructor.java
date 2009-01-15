/*
 * See LICENSE file in distribution for copyright and licensing information.
 */
package org.yaml.snakeyaml.constructor;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.ClassDescription;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * @see <a href="http://pyyaml.org/wiki/PyYAML">PyYAML< /a> for more information
 */
public class Constructor extends SafeConstructor {
    private Map<String, Class<? extends Object>> classTags;
    private Map<Class<? extends Object>, ClassDescription> classDefinitions;

    public Constructor() {
        this(Object.class);
    }

    public Constructor(Class<? extends Object> theRoot) {
        if (theRoot == null) {
            throw new NullPointerException("Root class must be provided.");
        }
        this.yamlConstructors.put(null, new ConstuctYamlObject());
        rootClass = theRoot;
        classTags = new HashMap<String, Class<? extends Object>>();
        classDefinitions = new HashMap<Class<? extends Object>, ClassDescription>();
    }

    /**
     * Make YAML aware how to parse a custom Class. If there is no root Class
     * assigned in constructor then the 'root' property of this definition is
     * respected.
     * 
     * @param definition
     *            to be added to the Constructor
     * @return the previous value associated with <tt>definition</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>definition</tt>.
     */
    public ClassDescription addClassDefinition(ClassDescription definition) {
        if (definition == null) {
            throw new NullPointerException("ClassDescription is required.");
        }
        if (rootClass == Object.class && definition.isRoot()) {
            rootClass = definition.getClazz();
        }
        String tag = definition.getTag();
        classTags.put(tag, definition.getClazz());
        return classDefinitions.put(definition.getClazz(), definition);
    }

    private class ConstuctYamlObject implements Construct {
        @SuppressWarnings("unchecked")
        public Object construct(Node node) {
            Object result = null;
            Class<? extends Object> customTag = classTags.get(node.getTag());
            try {
                Class cl;
                if (customTag == null) {
                    if (node.getTag().length() < "tag:yaml.org,2002:".length()) {
                        throw new YAMLException("Unknown tag: " + node.getTag());
                    }
                    String name = node.getTag().substring("tag:yaml.org,2002:".length());
                    cl = Class.forName(name);
                } else {
                    cl = customTag;
                }
                if (node instanceof MappingNode) {
                    MappingNode mnode = (MappingNode) node;
                    mnode.setType(cl);
                    result = constructMappingNode(mnode);
                } else if (node instanceof SequenceNode) {
                    SequenceNode snode = (SequenceNode) node;
                    List<Object> values = (List<Object>) constructSequence(snode);
                    Class[] parameterTypes = new Class[values.size()];
                    int index = 0;
                    for (Object parameter : values) {
                        parameterTypes[index] = parameter.getClass();
                        index++;
                    }
                    java.lang.reflect.Constructor javaConstructor = cl
                            .getConstructor(parameterTypes);
                    Object[] initargs = values.toArray();
                    result = javaConstructor.newInstance(initargs);
                } else {
                    ScalarNode snode = (ScalarNode) node;
                    Object value = constructScalar(snode);
                    java.lang.reflect.Constructor javaConstructor = cl.getConstructor(value
                            .getClass());
                    result = javaConstructor.newInstance(value);
                }
            } catch (Exception e) {
                throw new ConstructorException(null, null, "Can't construct a java object for "
                        + node.getTag() + "; exception=" + e.getMessage(), node.getStartMark());
            }
            return result;
        }
    }

    @Override
    protected Object callConstructor(Node node) {
        if (Object.class.equals(node.getType())) {
            return super.callConstructor(node);
        }
        Object result;
        if (node instanceof ScalarNode) {
            result = constructScalarNode((ScalarNode) node);
        } else if (node instanceof SequenceNode) {
            result = constructSequenceNode((SequenceNode) node);
        } else {
            result = constructMappingNode((MappingNode) node);
        }
        return result;
    }

    private Object constructScalarNode(ScalarNode node) {
        Class<? extends Object> c = node.getType();
        Object result;
        if (c.isPrimitive() || c == String.class || Number.class.isAssignableFrom(c)
                || c == Boolean.class || c == Date.class || c == Character.class
                || c == BigInteger.class) {
            if (c == String.class) {
                Construct stringContructor = yamlConstructors.get("tag:yaml.org,2002:str");
                result = stringContructor.construct((ScalarNode) node);
            } else if (c == Boolean.class || c == Boolean.TYPE) {
                Construct boolContructor = yamlConstructors.get("tag:yaml.org,2002:bool");
                result = boolContructor.construct((ScalarNode) node);
            } else if (c == Character.class || c == Character.TYPE) {
                Construct charContructor = yamlConstructors.get("tag:yaml.org,2002:str");
                String ch = (String) charContructor.construct((ScalarNode) node);
                if (ch.length() != 1) {
                    throw new YAMLException("Invalid node Character: '" + ch + "'; length: "
                            + ch.length());
                }
                result = new Character(ch.charAt(0));
            } else if (c == Date.class) {
                Construct dateContructor = yamlConstructors.get("tag:yaml.org,2002:timestamp");
                result = dateContructor.construct((ScalarNode) node);
            } else if (c == Float.class || c == Double.class || c == Float.TYPE || c == Double.TYPE) {
                Construct doubleContructor = yamlConstructors.get("tag:yaml.org,2002:float");
                result = doubleContructor.construct(node);
                if (c == Float.class || c == Float.TYPE) {
                    result = new Float((Double) result);
                }
            } else if (Number.class.isAssignableFrom(c) || c == Byte.TYPE || c == Short.TYPE
                    || c == Integer.TYPE || c == Long.TYPE) {
                Construct intContructor = yamlConstructors.get("tag:yaml.org,2002:int");
                result = intContructor.construct(node);
                if (c == Byte.class || c == Byte.TYPE) {
                    result = new Byte(result.toString());
                } else if (c == Short.class || c == Short.TYPE) {
                    result = new Short(result.toString());
                } else if (c == Integer.class || c == Integer.TYPE) {
                    result = new Integer(result.toString());
                } else if (c == Long.class || c == Long.TYPE) {
                    result = new Long(result.toString());
                } else if (c == BigInteger.class) {
                    result = new BigInteger(result.toString());
                } else {
                    throw new YAMLException("Unsupported Number class: " + c);
                }
            } else {
                throw new YAMLException("Unsupported class: " + c);
            }
        } else {
            try {
                // get value by BaseConstructor
                Object value = super.callConstructor(node);
                java.lang.reflect.Constructor<? extends Object> javaConstructor = c
                        .getConstructor(value.getClass());
                result = javaConstructor.newInstance(value);
            } catch (Exception e) {
                throw new YAMLException(e);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object constructSequenceNode(SequenceNode node) {
        List<Object> result;
        if (node.getType().isInterface()) {
            result = createDefaultList(node.getValue().size());
        } else {
            try {
                java.lang.reflect.Constructor javaConstructor = node.getType().getConstructor();
                result = (List<Object>) javaConstructor.newInstance();
            } catch (Exception e) {
                throw new YAMLException(e);
            }
        }
        List<Node> nodeValue = (List<Node>) node.getValue();
        for (Iterator<Node> iter = nodeValue.iterator(); iter.hasNext();) {
            Node valueNode = iter.next();
            Object value = constructObject(valueNode);
            result.add(value);
        }
        return result;
    }

    private Object constructMappingNode(MappingNode node) {
        Class<? extends Object> beanClass = node.getType();
        Object object;
        try {
            object = beanClass.newInstance();
        } catch (InstantiationException e) {
            throw new YAMLException(e);
        } catch (IllegalAccessException e) {
            throw new YAMLException(e);
        }
        List<Node[]> nodeValue = (List<Node[]>) node.getValue();
        for (Iterator<Node[]> iter = nodeValue.iterator(); iter.hasNext();) {
            Node[] tuple = iter.next();
            ScalarNode keyNode;
            if (tuple[0] instanceof ScalarNode) {
                keyNode = (ScalarNode) tuple[0];// key must be scalar
            } else {
                throw new YAMLException("Keys must be scalars but found: " + tuple[0]);
            }
            Node valueNode = tuple[1];
            // keys can only be Strings
            keyNode.setType(String.class);
            String key = (String) constructObject(keyNode);
            try {
                Property property = getProperty(beanClass, key);
                if (property == null)
                    throw new YAMLException("Unable to find property '" + key + "' on class: "
                            + beanClass.getName());
                valueNode.setType(property.getType());
                // TODO
                ClassDescription memberDescription = classDefinitions.get(beanClass);
                if (memberDescription != null) {
                    if (valueNode instanceof SequenceNode) {
                        SequenceNode snode = (SequenceNode) valueNode;
                        Class<? extends Object> memberType = memberDescription
                                .getListPropertyType(key);
                        if (memberType != null) {
                            snode.setListType(memberType);
                        }
                    } else {
                        // TODO check for map
                    }
                }
                Object value = constructObject(valueNode);
                property.set(object, value);
            } catch (Exception e) {
                throw new YAMLException(e);
            }
        }
        return object;
    }

    protected Property getProperty(Class<? extends Object> type, String name)
            throws IntrospectionException {
        for (PropertyDescriptor property : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
            if (property.getName().equals(name)) {
                if (property.getReadMethod() != null && property.getWriteMethod() != null)
                    return new MethodProperty(property);
                break;
            }
        }
        for (Field field : type.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
                    || Modifier.isTransient(modifiers))
                continue;
            if (field.getName().equals(name))
                return new FieldProperty(field);
        }
        return null;
    }
}
