package com.jsoniter.spi;

import com.jsoniter.JsonException;
import sun.net.www.content.text.Generic;

import java.lang.reflect.*;
import java.util.*;

public class ExtensionManager {

    static List<Extension> extensions = new ArrayList<Extension>();
    static volatile Map<String, Encoder> encoders = new HashMap<String, Encoder>();
    static volatile Map<String, Decoder> decoders = new HashMap<String, Decoder>();

    public static void registerExtension(Extension extension) {
        extensions.add(extension);
    }

    public static List<Extension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    public static void registerTypeDecoder(Class clazz, Decoder decoder) {
        addNewDecoder(TypeLiteral.generateDecoderCacheKey(clazz), decoder);
    }

    public static void registerTypeDecoder(TypeLiteral typeLiteral, Decoder decoder) {
        addNewDecoder(typeLiteral.getDecoderCacheKey(), decoder);
    }

    public static void registerFieldDecoder(Class clazz, String field, Decoder decoder) {
        addNewDecoder(field + "@" + TypeLiteral.generateDecoderCacheKey(clazz), decoder);
    }

    public static void registerFieldDecoder(TypeLiteral typeLiteral, String field, Decoder decoder) {
        addNewDecoder(field + "@" + typeLiteral.getDecoderCacheKey(), decoder);
    }

    public static void registerTypeEncoder(Class clazz, Encoder encoder) {
        addNewEncoder(TypeLiteral.generateEncoderCacheKey(clazz), encoder);
    }

    public static void registerTypeEncoder(TypeLiteral typeLiteral, Encoder encoder) {
        addNewEncoder(typeLiteral.getDecoderCacheKey(), encoder);
    }

    public static void registerFieldEncoder(Class clazz, String field, Encoder encoder) {
        addNewEncoder(field + "@" + TypeLiteral.generateEncoderCacheKey(clazz), encoder);
    }

    public static void registerFieldEncoder(TypeLiteral typeLiteral, String field, Encoder encoder) {
        addNewEncoder(field + "@" + typeLiteral.getDecoderCacheKey(), encoder);
    }

    public static Decoder getDecoder(String cacheKey) {
        return decoders.get(cacheKey);
    }

    public synchronized static void addNewDecoder(String cacheKey, Decoder decoder) {
        HashMap<String, Decoder> newCache = new HashMap<String, Decoder>(decoders);
        newCache.put(cacheKey, decoder);
        decoders = newCache;
    }

    public static Encoder getEncoder(String cacheKey) {
        if (cacheKey.startsWith("decoder")) {
            throw new RuntimeException();
        }
        return encoders.get(cacheKey);
    }

    public synchronized static void addNewEncoder(String cacheKey, Encoder encoder) {
        HashMap<String, Encoder> newCache = new HashMap<String, Encoder>(encoders);
        newCache.put(cacheKey, encoder);
        encoders = newCache;
    }

    public static ClassDescriptor getClassDescriptor(Class clazz, boolean includingPrivate) {
        Map<String, Type> lookup = collectTypeVariableLookup(clazz);
        ClassDescriptor desc = new ClassDescriptor();
        desc.clazz = clazz;
        desc.lookup = lookup;
        desc.ctor = getCtor(clazz);
        desc.fields = getFields(lookup, clazz, includingPrivate);
        desc.setters = getSetters(lookup, clazz, includingPrivate);
        desc.getters = getGetters(lookup, clazz, includingPrivate);
        for (Extension extension : extensions) {
            extension.updateClassDescriptor(desc);
        }
        if (includingPrivate) {
            if (desc.ctor.ctor != null) {
                desc.ctor.ctor.setAccessible(true);
            }
            if (desc.ctor.staticFactory != null) {
                desc.ctor.staticFactory.setAccessible(true);
            }
            for (SetterDescriptor setter : desc.setters) {
                setter.method.setAccessible(true);
            }
        }
        for (Binding binding : desc.allDecoderBindings()) {
            if (binding.fromNames == null) {
                binding.fromNames = new String[]{binding.name};
            }
            if (binding.field != null && includingPrivate) {
                binding.field.setAccessible(true);
            }
        }
        for (Binding binding : desc.allEncoderBindings()) {
            if (binding.toNames == null) {
                binding.toNames = new String[]{binding.name};
            }
            if (binding.field != null && includingPrivate) {
                binding.field.setAccessible(true);
            }
        }
        return desc;
    }

    private static ConstructorDescriptor getCtor(Class clazz) {
        ConstructorDescriptor cctor = new ConstructorDescriptor();
        try {
            cctor.ctor = clazz.getDeclaredConstructor();
        } catch (Exception e) {
            cctor.ctor = null;
        }
        return cctor;
    }

    private static List<Binding> getFields(Map<String, Type> lookup, Class clazz, boolean includingPrivate) {
        ArrayList<Binding> bindings = new ArrayList<Binding>();
        for (Field field : getAllFields(clazz, includingPrivate)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (includingPrivate) {
                field.setAccessible(true);
            }
            Binding binding = createBindingFromField(lookup, clazz, field);
            bindings.add(binding);
        }
        return bindings;
    }

    private static Binding createBindingFromField(Map<String, Type> lookup, Class clazz, Field field) {
        Binding binding = new Binding(clazz, lookup, field.getGenericType());
        binding.fromNames = new String[]{field.getName()};
        binding.name = field.getName();
        binding.annotations = field.getAnnotations();
        binding.field = field;
        return binding;
    }

    private static List<Field> getAllFields(Class clazz, boolean includingPrivate) {
        List<Field> allFields = Arrays.asList(clazz.getFields());
        if (includingPrivate) {
            allFields = new ArrayList<Field>();
            Class current = clazz;
            while (current != null) {
                allFields.addAll(Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }
        }
        return allFields;
    }

    private static List<SetterDescriptor> getSetters(Map<String, Type> lookup, Class clazz, boolean includingPrivate) {
        ArrayList<SetterDescriptor> setters = new ArrayList<SetterDescriptor>();
        List<Method> allMethods = Arrays.asList(clazz.getMethods());
        if (includingPrivate) {
            allMethods = new ArrayList<Method>();
            Class current = clazz;
            while (current != null) {
                allMethods.addAll(Arrays.asList(current.getDeclaredMethods()));
                current = current.getSuperclass();
            }
        }
        for (Method method : allMethods) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String methodName = method.getName();
            if (methodName.length() < 4) {
                continue;
            }
            if (!methodName.startsWith("set")) {
                continue;
            }
            Type[] paramTypes = method.getGenericParameterTypes();
            if (paramTypes.length != 1) {
                continue;
            }
            if (includingPrivate) {
                method.setAccessible(true);
            }
            String fromName = methodName.substring("set".length());
            char[] fromNameChars = fromName.toCharArray();
            fromNameChars[0] = Character.toLowerCase(fromNameChars[0]);
            fromName = new String(fromNameChars);
            SetterDescriptor setter = new SetterDescriptor();
            setter.method = method;
            setter.methodName = methodName;
            Binding param = new Binding(clazz, lookup, paramTypes[0]);
            param.fromNames = new String[]{fromName};
            param.name = fromName;
            param.clazz = clazz;
            setter.parameters.add(param);
            setters.add(setter);
        }
        return setters;
    }

    private static List<Binding> getGetters(Map<String, Type> lookup, Class clazz, boolean includingPrivate) {
        ArrayList<Binding> getters = new ArrayList<Binding>();
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String methodName = method.getName();
            if ("getClass".equals(methodName)) {
                continue;
            }
            if (methodName.length() < 4) {
                continue;
            }
            if (!methodName.startsWith("get")) {
                continue;
            }
            if (method.getGenericParameterTypes().length != 0) {
                continue;
            }
            String toName = methodName.substring("get".length());
            char[] fromNameChars = toName.toCharArray();
            fromNameChars[0] = Character.toLowerCase(fromNameChars[0]);
            toName = new String(fromNameChars);
            Binding getter = new Binding(clazz, lookup, method.getGenericReturnType());
            getter.toNames = new String[]{toName};
            getter.name = methodName + "()";
            getters.add(getter);
        }
        return getters;
    }

    public static void dump() {
        for (String cacheKey : decoders.keySet()) {
            System.err.println(cacheKey);
        }
        for (String cacheKey : encoders.keySet()) {
            System.err.println(cacheKey);
        }
    }

    private static Map<String, Type> collectTypeVariableLookup(Type type) {
        HashMap<String, Type> vars = new HashMap<String, Type>();
        if (null == type) {
            return vars;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type[] actualTypeArguments = pType.getActualTypeArguments();
            Class clazz = (Class) pType.getRawType();
            for (int i = 0; i < clazz.getTypeParameters().length; i++) {
                TypeVariable variable = clazz.getTypeParameters()[i];
                vars.put(variable.getName() + "@" + clazz.getCanonicalName(), actualTypeArguments[i]);
            }
            vars.putAll(collectTypeVariableLookup(clazz.getGenericSuperclass()));
            return vars;
        }
        if (type instanceof Class) {
            Class clazz = (Class) type;
            vars.putAll(collectTypeVariableLookup(clazz.getGenericSuperclass()));
            return vars;
        }
        throw new JsonException("unexpected type: " + type);
    }

    public static void disableDynamicCodegen() {

    }
}
