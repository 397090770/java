package com.jsoniter;

import com.jsoniter.spi.Decoder;
import com.jsoniter.spi.ExtensionManager;
import com.jsoniter.spi.TypeLiteral;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

// only uesd by generated code to access decoder
public class CodegenAccess {

    public static void addMissingField(List missingFields, long tracker, long mask, String fieldName) {
        if ((tracker & mask) == 0) {
            missingFields.add(fieldName);
        }
    }

    public static <T extends Collection> T reuseCollection(T col) {
        col.clear();
        return col;
    }

    public static Object existingObject(JsonIterator iter) {
        return iter.existingObject;
    }

    public static Object resetExistingObject(JsonIterator iter) {
        Object obj = iter.existingObject;
        iter.existingObject = null;
        return obj;
    }

    public static void setExistingObject(JsonIterator iter, Object obj) {
        iter.existingObject = obj;
    }

    public static byte nextToken(JsonIterator iter) throws IOException {
        return iter.nextToken();
    }

    public static final <T> T read(JsonIterator iter, TypeLiteral<T> typeLiteral) throws IOException {
        TypeLiteral.NativeType nativeType = typeLiteral.getNativeType();
        if (nativeType != null) {
            switch (nativeType) {
                case FLOAT:
                    return (T) Float.valueOf(iter.readFloat());
                case DOUBLE:
                    return (T) Double.valueOf(iter.readDouble());
                case BOOLEAN:
                    return (T) Boolean.valueOf(iter.readBoolean());
                case BYTE:
                    return (T) Byte.valueOf((byte) iter.readShort());
                case SHORT:
                    return (T) Short.valueOf(iter.readShort());
                case INT:
                    return (T) Integer.valueOf(iter.readInt());
                case CHAR:
                    return (T) Character.valueOf((char) iter.readInt());
                case LONG:
                    return (T) Long.valueOf(iter.readLong());
                case BIG_DECIMAL:
                    return (T) iter.readBigDecimal();
                case BIG_INTEGER:
                    return (T) iter.readBigInteger();
                case STRING:
                    return (T) iter.readString();
                case OBJECT:
                    return (T) iter.readAnyObject();
                case ANY:
                    return (T) iter.readAny();
                default:
                    throw new JsonException("unsupported native type: " + nativeType);
            }
        } else {
            String cacheKey = typeLiteral.getDecoderCacheKey();
            return (T) Codegen.getDecoder(cacheKey, typeLiteral.getType()).decode(iter);
        }
    }

    public static final boolean readBoolean(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.BooleanDecoder) ExtensionManager.getDecoder(cacheKey)).decodeBoolean(iter);
    }

    public static final short readShort(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.ShortDecoder) ExtensionManager.getDecoder(cacheKey)).decodeShort(iter);
    }

    public static final int readInt(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.IntDecoder) ExtensionManager.getDecoder(cacheKey)).decodeInt(iter);
    }

    public static final long readLong(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.LongDecoder) ExtensionManager.getDecoder(cacheKey)).decodeLong(iter);
    }

    public static final float readFloat(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.FloatDecoder) ExtensionManager.getDecoder(cacheKey)).decodeFloat(iter);
    }

    public static final double readDouble(String cacheKey, JsonIterator iter) throws IOException {
        return ((Decoder.DoubleDecoder) ExtensionManager.getDecoder(cacheKey)).decodeDouble(iter);
    }

    public static final <T> T read(String cacheKey, JsonIterator iter) throws IOException {
        return (T) Codegen.getDecoder(cacheKey, null).decode(iter);
    }

    public static boolean readArrayStart(JsonIterator iter) throws IOException {
        byte c = iter.nextToken();
        if (c != '[') {
            throw iter.reportError("readArrayStart", "expect [ or n");
        }
        c = iter.nextToken();
        if (c == ']') {
            return false;
        }
        iter.unreadByte();
        return true;
    }

    public static boolean readObjectStart(JsonIterator iter) throws IOException {
        byte c = iter.nextToken();
        if (c != '{') {
            throw iter.reportError("readObjectStart", "expect { or n, found: " + (char) c);
        }
        c = iter.nextToken();
        if (c == '}') {
            return false;
        }
        iter.unreadByte();
        return true;
    }

    public static void reportIncompleteObject(JsonIterator iter) {
        throw iter.reportError("genObject", "expect }");
    }

    public static void reportIncompleteArray(JsonIterator iter) {
        throw iter.reportError("genArray", "expect ]");
    }

    public static final String readObjectFieldAsString(JsonIterator iter) throws IOException {
        String field = iter.readString();
        if (iter.nextToken() != ':') {
            throw iter.reportError("readObjectFieldAsString", "expect :");
        }
        return field;
    }

    public static final int readObjectFieldAsHash(JsonIterator iter) throws IOException {
        if (iter.nextToken() != '"') {
            throw iter.reportError("readObjectFieldAsHash", "expect \"");
        }
        long hash = 0x811c9dc5;
        for (; ; ) {
            byte c = 0;
            int i = iter.head;
            for (; i < iter.tail; i++) {
                c = iter.buf[i];
                if (c == '"') {
                    break;
                }
                hash ^= c;
                hash *= 0x1000193;
            }
            if (c == '"') {
                iter.head = i + 1;
                if (iter.nextToken() != ':') {
                    throw iter.reportError("readObjectFieldAsHash", "expect :");
                }
                return (int) hash;
            }
            if (!iter.loadMore()) {
                throw iter.reportError("readObjectFieldAsHash", "unmatched quote");
            }
        }
    }

    public static final Slice readObjectFieldAsSlice(JsonIterator iter) throws IOException {
        if (iter.nextToken() != '"') {
            throw iter.reportError("readObjectFieldAsSlice", "expect \"");
        }
        Slice field = IterImplString.readSlice(iter);
        boolean notCopied = field != null;
        if (skipWhitespacesWithoutLoadMore(iter)) {
            if (notCopied) {
                byte[] newBuf = new byte[field.len];
                System.arraycopy(field.data, field.head, newBuf, 0, field.len);
                field.data = newBuf;
                field.head = 0;
                field.len = newBuf.length;
            }
            if (!iter.loadMore()) {
                throw iter.reportError("readObjectFieldAsSlice", "expect : after object field");
            }
        }
        if (iter.buf[iter.head] != ':') {
            throw iter.reportError("readObjectFieldAsSlice", "expect : after object field");
        }
        iter.head++;
        if (skipWhitespacesWithoutLoadMore(iter)) {
            if (notCopied) {
                byte[] newBuf = new byte[field.len];
                System.arraycopy(field.data, field.head, newBuf, 0, field.len);
                field.data = newBuf;
                field.head = 0;
                field.len = newBuf.length;
            }
            if (!iter.loadMore()) {
                throw iter.reportError("readObjectFieldAsSlice", "expect : after object field");
            }
        }
        return field;
    }

    private final static boolean skipWhitespacesWithoutLoadMore(JsonIterator iter) throws IOException {
        for (int i = iter.head; i < iter.tail; i++) {
            byte c = iter.buf[i];
            switch (c) {
                case ' ':
                case '\n':
                case '\t':
                case '\r':
                    continue;
            }
            iter.head = i;
            return false;
        }
        return true;
    }

    public static void staticGenDecoders(TypeLiteral[] typeLiterals) {
        Codegen.staticGenDecoders(typeLiterals);
    }
}
