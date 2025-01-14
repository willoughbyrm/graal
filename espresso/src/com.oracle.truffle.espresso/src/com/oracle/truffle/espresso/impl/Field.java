/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.jdwp.api.FieldBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Represents a resolved Espresso field.
 */
public final class Field extends Member<Type> implements FieldRef {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final ObjectKlass holder;
    private volatile Klass typeKlassCache;

    @CompilationFinal private Symbol<ModifiedUTF8> genericSignature = null;

    public Field(ObjectKlass holder, LinkedField linkedField, boolean hidden) {
        super(hidden ? null : linkedField.getType(), linkedField.getName());
        this.linkedField = linkedField;
        this.holder = holder;
    }

    public Symbol<Type> getType() {
        return descriptor;
    }

    public Attribute[] getAttributes() {
        return linkedField.getParserField().getAttributes();
    }

    public Symbol<ModifiedUTF8> getGenericSignature() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) linkedField.getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ModifiedUTF8.fromSymbol(getType());
            } else {
                genericSignature = holder.getConstantPool().symbolAt(attr.getSignatureIndex());
            }
        }
        return genericSignature;
    }

    public boolean isHidden() {
        return getDescriptor() == null;
    }

    public JavaKind getKind() {
        return linkedField.getKind();
    }

    @Override
    public int getModifiers() {
        return linkedField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS;
    }

    @Override
    public ObjectKlass getDeclaringKlass() {
        return holder;
    }

    /**
     * The slot serves as the position in the `field table` of the ObjectKlass.
     */
    public int getSlot() {
        return linkedField.getSlot();
    }

    @Override
    public String toString() {
        return getDeclaringKlass().getNameAsString() + "." + getName() + ": " + getType();
    }

    public Klass resolveTypeKlass() {
        Klass tk = typeKlassCache;
        if (tk == null) {
            synchronized (this) {
                tk = typeKlassCache;
                if (tk == null) {
                    tk = getDeclaringKlass().getMeta().resolveSymbolOrFail(getType(),
                                    getDeclaringKlass().getDefiningClassLoader(),
                                    getDeclaringKlass().protectionDomain());
                    typeKlassCache = tk;
                }
            }
        }
        return typeKlassCache;
    }

    public Attribute getAttribute(Symbol<Name> attrName) {
        return linkedField.getAttribute(attrName);
    }

    public static Field getReflectiveFieldRoot(StaticObject seed, Meta meta) {
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) meta.HIDDEN_FIELD_KEY.getHiddenObject(curField);
            if (target == null) {
                curField = meta.java_lang_reflect_Field_root.getObject(curField);
            }
        }
        return target;
    }

    public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
        getDeclaringKlass().getContext().getRegistries().checkLoadingConstraint(getType(), loader1, loader2);
    }

    // region Field accesses

    // region Generic
    public Object get(StaticObject obj) {
        return get(obj, false);
    }

    public Object get(StaticObject obj, boolean forceVolatile) {
        // @formatter:off
        switch (getKind()) {
            case Boolean : return getBoolean(obj, forceVolatile);
            case Byte    : return getByte(obj, forceVolatile);
            case Short   : return getShort(obj, forceVolatile);
            case Char    : return getChar(obj, forceVolatile);
            case Int     : return getInt(obj, forceVolatile);
            case Float   : return getFloat(obj, forceVolatile);
            case Long    : return getLong(obj, forceVolatile);
            case Double  : return getDouble(obj, forceVolatile);
            case Object  : return getObject(obj, forceVolatile);
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public void set(StaticObject obj, Object value) {
        set(obj, value, false);
    }

    public void set(StaticObject obj, Object value, boolean forceVolatile) {
        // @formatter:off
        switch (getKind()) {
            case Boolean : setBoolean(obj, (boolean) value, forceVolatile);    break;
            case Byte    : setByte(obj, (byte) value, forceVolatile);          break;
            case Short   : setShort(obj, (short) value, forceVolatile);        break;
            case Char    : setChar(obj, (char) value, forceVolatile);          break;
            case Int     : setInt(obj, (int) value, forceVolatile);            break;
            case Float   : setFloat(obj, (float) value, forceVolatile);        break;
            case Long    : setLong(obj, (long) value, forceVolatile);          break;
            case Double  : setDouble(obj, (double) value, forceVolatile);      break;
            case Object  : setObject(obj, value, forceVolatile);               break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsBoolean(meta, obj, defaultIfNull, false);
    }

    public boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asBoolean(val, defaultIfNull);
    }

    public byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsByte(meta, obj, defaultIfNull, false);
    }

    public byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asByte(val, defaultIfNull);
    }

    public short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsShort(meta, obj, defaultIfNull, false);
    }

    public short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asShort(val, defaultIfNull);
    }

    public char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsChar(meta, obj, defaultIfNull, false);
    }

    public char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asChar(val, defaultIfNull);
    }

    public int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsInt(meta, obj, defaultIfNull, false);
    }

    public int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asInt(val, defaultIfNull);
    }

    public float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsFloat(meta, obj, defaultIfNull, false);
    }

    public float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asFloat(val, defaultIfNull);
    }

    public long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsLong(meta, obj, defaultIfNull, false);
    }

    public long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asLong(val, defaultIfNull);
    }

    public double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsDouble(meta, obj, defaultIfNull, false);
    }

    public double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asDouble(val, defaultIfNull);
    }

    public StaticObject getAsObject(Meta meta, StaticObject obj) {
        return getAsObject(meta, obj, false);
    }

    public StaticObject getAsObject(Meta meta, StaticObject obj, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asObject(val);
    }
    // endregion Generic

    // region Object

    // region helper methods
    private Object getObjectHelper(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getObjectVolatile(obj);
        } else {
            return linkedField.getObject(obj);
        }
    }

    private void setObjectHelper(StaticObject obj, Object value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setObjectVolatile(obj, value);
        } else {
            linkedField.setObject(obj, value);
        }
    }
    // endregion helper methods

    // To access hidden fields, use the dedicated `(g|s)etHiddenObjectField` methods
    public StaticObject getObject(StaticObject obj) {
        return getObject(obj, false);
    }

    public StaticObject getObject(StaticObject obj, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use getHiddenObject";
        return (StaticObject) getObjectHelper(obj, forceVolatile);
    }

    public void setObject(StaticObject obj, Object value) {
        setObject(obj, value, false);
    }

    public void setObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use setHiddenObject";
        setObjectHelper(obj, value, forceVolatile);
    }

    public StaticObject getAndSetObject(StaticObject obj, StaticObject value) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return (StaticObject) linkedField.getAndSetObject(obj, value);
    }

    public boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapObject(obj, before, after);
    }

    // region hidden Object
    public Object getHiddenObject(StaticObject obj) {
        return getHiddenObject(obj, false);
    }

    public Object getHiddenObject(StaticObject obj, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use getObject";
        return getObjectHelper(obj, forceVolatile);
    }

    public void setHiddenObject(StaticObject obj, Object value) {
        setHiddenObject(obj, value, false);
    }

    public void setHiddenObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use setObject";
        setObjectHelper(obj, value, forceVolatile);
    }
    // endregion Hidden Object
    // endregion Object

    // region boolean
    public boolean getBoolean(StaticObject obj) {
        return getBoolean(obj, false);
    }

    public boolean getBoolean(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getBooleanVolatile(obj);
        } else {
            return linkedField.getBoolean(obj);
        }
    }

    public void setBoolean(StaticObject obj, boolean value) {
        setBoolean(obj, value, false);
    }

    public void setBoolean(StaticObject obj, boolean value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setBooleanVolatile(obj, value);
        } else {
            linkedField.setBoolean(obj, value);
        }
    }
    // endregion boolean

    // region byte
    public byte getByte(StaticObject obj) {
        return getByte(obj, false);
    }

    public byte getByte(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getByteVolatile(obj);
        } else {
            return linkedField.getByte(obj);
        }
    }

    public void setByte(StaticObject obj, byte value) {
        setByte(obj, value, false);
    }

    public void setByte(StaticObject obj, byte value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setByteVolatile(obj, value);
        } else {
            linkedField.setByte(obj, value);
        }
    }
    // endregion byte

    // region char
    public char getChar(StaticObject obj) {
        return getChar(obj, false);
    }

    public char getChar(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getCharVolatile(obj);
        } else {
            return linkedField.getChar(obj);
        }
    }

    public void setChar(StaticObject obj, char value) {
        setChar(obj, value, false);
    }

    public void setChar(StaticObject obj, char value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setCharVolatile(obj, value);
        } else {
            linkedField.setChar(obj, value);
        }
    }
    // endregion char

    // region double
    public double getDouble(StaticObject obj) {
        return getDouble(obj, false);
    }

    public double getDouble(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getDoubleVolatile(obj);
        } else {
            return linkedField.getDouble(obj);
        }
    }

    public void setDouble(StaticObject obj, double value) {
        setDouble(obj, value, false);
    }

    public void setDouble(StaticObject obj, double value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setDoubleVolatile(obj, value);
        } else {
            linkedField.setDouble(obj, value);
        }
    }

    // endregion double

    // region float
    public float getFloat(StaticObject obj) {
        return getFloat(obj, false);
    }

    public float getFloat(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getFloatVolatile(obj);
        } else {
            return linkedField.getFloat(obj);
        }
    }

    public void setFloat(StaticObject obj, float value) {
        setFloat(obj, value, false);
    }

    public void setFloat(StaticObject obj, float value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setFloatVolatile(obj, value);
        } else {
            linkedField.setFloat(obj, value);
        }
    }
    // endregion float

    // region int
    public int getInt(StaticObject obj) {
        return getInt(obj, false);
    }

    public int getInt(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getIntVolatile(obj);
        } else {
            return linkedField.getInt(obj);
        }
    }

    public void setInt(StaticObject obj, int value) {
        setInt(obj, value, false);
    }

    public void setInt(StaticObject obj, int value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setIntVolatile(obj, value);
        } else {
            linkedField.setInt(obj, value);
        }
    }

    public boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapInt(obj, before, after);
    }
    // endregion int

    // region long
    public long getLong(StaticObject obj) {
        return getLong(obj, false);
    }

    public long getLong(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (isVolatile() || forceVolatile) {
            return linkedField.getLongVolatile(obj);
        } else {
            return linkedField.getLong(obj);
        }
    }

    public void setLong(StaticObject obj, long value) {
        setLong(obj, value, false);
    }

    public void setLong(StaticObject obj, long value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (isVolatile() || forceVolatile) {
            linkedField.setLongVolatile(obj, value);
        } else {
            linkedField.setLong(obj, value);
        }
    }

    public boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        return linkedField.compareAndSwapLong(obj, before, after);
    }
    // endregion long

    // region short
    public short getShort(StaticObject obj) {
        return getShort(obj, false);
    }

    public short getShort(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getShortVolatile(obj);
        } else {
            return linkedField.getShort(obj);
        }
    }

    public void setShort(StaticObject obj, short value) {
        setShort(obj, value, false);
    }

    public void setShort(StaticObject obj, short value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setShortVolatile(obj, value);
        } else {
            linkedField.setShort(obj, value);
        }
    }
    // endregion short

    // endregion Field accesses

    // region jdwp-specific
    @Override
    public byte getTagConstant() {
        return getKind().toTagConstant();
    }

    @Override
    public String getNameAsString() {
        return super.getName().toString();
    }

    @Override
    public String getTypeAsString() {
        return super.getDescriptor().toString();
    }

    @Override
    public String getGenericSignatureAsString() {
        Symbol<ModifiedUTF8> signature = getGenericSignature();
        return signature.toString();
    }

    @Override
    public Object getValue(Object self) {
        return get((StaticObject) self);
    }

    @Override
    public void setValue(Object self, Object value) {
        set((StaticObject) self, value);
    }

    private final StableBoolean hasActiveBreakpoints = new StableBoolean(false);

    // array with maximum size 2, one access info and/or one modification info.
    private FieldBreakpoint[] infos = null;

    @Override
    public boolean hasActiveBreakpoint() {
        return hasActiveBreakpoints.get();
    }

    @Override
    public FieldBreakpoint[] getFieldBreakpointInfos() {
        return infos;
    }

    @Override
    public void addFieldBreakpointInfo(FieldBreakpoint info) {
        if (infos == null) {
            infos = new FieldBreakpoint[]{info};
            hasActiveBreakpoints.set(true);
            return;
        }

        int length = infos.length;
        FieldBreakpoint[] temp = new FieldBreakpoint[length + 1];
        System.arraycopy(infos, 0, temp, 0, length);
        temp[length] = info;
        infos = temp;
        hasActiveBreakpoints.set(true);
    }

    @Override
    public void removeFieldBreakpointInfo(int requestId) {
        // shrink the array to avoid null values
        switch (infos.length) {
            case 0:
                throw new RuntimeException("Field: " + getNameAsString() + " should contain field breakpoint info");
            case 1:
                infos = null;
                hasActiveBreakpoints.set(false);
                return;
            case 2:
                FieldBreakpoint[] temp = new FieldBreakpoint[1];
                FieldBreakpoint info = infos[0];
                if (info.getRequestId() == requestId) {
                    // remove index 0, but keep info at index 1
                    temp[0] = infos[1];
                    infos = temp;
                    return;
                }
                info = infos[1];
                if (info.getRequestId() == requestId) {
                    // remove index 1, but keep info at index 0
                    temp[0] = infos[0];
                    infos = temp;
                    return;
                }
        }
    }

    /**
     * Helper class that uses an assumption to switch between two "stable" states efficiently.
     * Copied from DebuggerSession with modifications to the set method to make it thread safe (but
     * slower on the slow path).
     */
    static final class StableBoolean {

        @CompilationFinal private volatile Assumption unchanged;
        @CompilationFinal private volatile boolean value;

        StableBoolean(boolean initialValue) {
            this.value = initialValue;
            this.unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
        }

        @SuppressFBWarnings(value = "UG_SYNC_SET_UNSYNC_GET", justification = "The get method returns a volatile field.")
        public boolean get() {
            if (!unchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return value;
        }

        /**
         * This method needs to be behind a boundary due to the fact that compiled code will
         * constant fold the value, hence the first check might yield a wrong result.
         */
        @CompilerDirectives.TruffleBoundary
        public synchronized void set(boolean value) {
            if (this.value != value) {
                this.value = value;
                Assumption old = this.unchanged;
                unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
                old.invalidate();
            }
        }
    }

    // endregion jdwp-specific
}
