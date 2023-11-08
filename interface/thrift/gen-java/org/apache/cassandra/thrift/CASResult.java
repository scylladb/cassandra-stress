/**
 * Autogenerated by Thrift Compiler (0.14.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.cassandra.thrift;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.0)", date = "2023-11-08")
public class CASResult implements org.apache.thrift.TBase<CASResult, CASResult._Fields>, java.io.Serializable, Cloneable, Comparable<CASResult> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("CASResult");

  private static final org.apache.thrift.protocol.TField SUCCESS_FIELD_DESC = new org.apache.thrift.protocol.TField("success", org.apache.thrift.protocol.TType.BOOL, (short)1);
  private static final org.apache.thrift.protocol.TField CURRENT_VALUES_FIELD_DESC = new org.apache.thrift.protocol.TField("current_values", org.apache.thrift.protocol.TType.LIST, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new CASResultStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new CASResultTupleSchemeFactory();

  public boolean success; // required
  public @org.apache.thrift.annotation.Nullable java.util.List<Column> current_values; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    SUCCESS((short)1, "success"),
    CURRENT_VALUES((short)2, "current_values");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // SUCCESS
          return SUCCESS;
        case 2: // CURRENT_VALUES
          return CURRENT_VALUES;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __SUCCESS_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.CURRENT_VALUES};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.SUCCESS, new org.apache.thrift.meta_data.FieldMetaData("success", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    tmpMap.put(_Fields.CURRENT_VALUES, new org.apache.thrift.meta_data.FieldMetaData("current_values", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, Column.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(CASResult.class, metaDataMap);
  }

  public CASResult() {
  }

  public CASResult(
    boolean success)
  {
    this();
    this.success = success;
    setSuccessIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public CASResult(CASResult other) {
    __isset_bitfield = other.__isset_bitfield;
    this.success = other.success;
    if (other.isSetCurrent_values()) {
      java.util.List<Column> __this__current_values = new java.util.ArrayList<Column>(other.current_values.size());
      for (Column other_element : other.current_values) {
        __this__current_values.add(new Column(other_element));
      }
      this.current_values = __this__current_values;
    }
  }

  public CASResult deepCopy() {
    return new CASResult(this);
  }

  @Override
  public void clear() {
    setSuccessIsSet(false);
    this.success = false;
    this.current_values = null;
  }

  public boolean isSuccess() {
    return this.success;
  }

  public CASResult setSuccess(boolean success) {
    this.success = success;
    setSuccessIsSet(true);
    return this;
  }

  public void unsetSuccess() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __SUCCESS_ISSET_ID);
  }

  /** Returns true if field success is set (has been assigned a value) and false otherwise */
  public boolean isSetSuccess() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __SUCCESS_ISSET_ID);
  }

  public void setSuccessIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __SUCCESS_ISSET_ID, value);
  }

  public int getCurrent_valuesSize() {
    return (this.current_values == null) ? 0 : this.current_values.size();
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.Iterator<Column> getCurrent_valuesIterator() {
    return (this.current_values == null) ? null : this.current_values.iterator();
  }

  public void addToCurrent_values(Column elem) {
    if (this.current_values == null) {
      this.current_values = new java.util.ArrayList<Column>();
    }
    this.current_values.add(elem);
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.List<Column> getCurrent_values() {
    return this.current_values;
  }

  public CASResult setCurrent_values(@org.apache.thrift.annotation.Nullable java.util.List<Column> current_values) {
    this.current_values = current_values;
    return this;
  }

  public void unsetCurrent_values() {
    this.current_values = null;
  }

  /** Returns true if field current_values is set (has been assigned a value) and false otherwise */
  public boolean isSetCurrent_values() {
    return this.current_values != null;
  }

  public void setCurrent_valuesIsSet(boolean value) {
    if (!value) {
      this.current_values = null;
    }
  }

  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case SUCCESS:
      if (value == null) {
        unsetSuccess();
      } else {
        setSuccess((java.lang.Boolean)value);
      }
      break;

    case CURRENT_VALUES:
      if (value == null) {
        unsetCurrent_values();
      } else {
        setCurrent_values((java.util.List<Column>)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case SUCCESS:
      return isSuccess();

    case CURRENT_VALUES:
      return getCurrent_values();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case SUCCESS:
      return isSetSuccess();
    case CURRENT_VALUES:
      return isSetCurrent_values();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof CASResult)
      return this.equals((CASResult)that);
    return false;
  }

  public boolean equals(CASResult that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_success = true;
    boolean that_present_success = true;
    if (this_present_success || that_present_success) {
      if (!(this_present_success && that_present_success))
        return false;
      if (this.success != that.success)
        return false;
    }

    boolean this_present_current_values = true && this.isSetCurrent_values();
    boolean that_present_current_values = true && that.isSetCurrent_values();
    if (this_present_current_values || that_present_current_values) {
      if (!(this_present_current_values && that_present_current_values))
        return false;
      if (!this.current_values.equals(that.current_values))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((success) ? 131071 : 524287);

    hashCode = hashCode * 8191 + ((isSetCurrent_values()) ? 131071 : 524287);
    if (isSetCurrent_values())
      hashCode = hashCode * 8191 + current_values.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(CASResult other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetSuccess(), other.isSetSuccess());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSuccess()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.success, other.success);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetCurrent_values(), other.isSetCurrent_values());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCurrent_values()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.current_values, other.current_values);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  @org.apache.thrift.annotation.Nullable
  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("CASResult(");
    boolean first = true;

    sb.append("success:");
    sb.append(this.success);
    first = false;
    if (isSetCurrent_values()) {
      if (!first) sb.append(", ");
      sb.append("current_values:");
      if (this.current_values == null) {
        sb.append("null");
      } else {
        sb.append(this.current_values);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // alas, we cannot check 'success' because it's a primitive and you chose the non-beans generator.
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class CASResultStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public CASResultStandardScheme getScheme() {
      return new CASResultStandardScheme();
    }
  }

  private static class CASResultStandardScheme extends org.apache.thrift.scheme.StandardScheme<CASResult> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, CASResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // SUCCESS
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.success = iprot.readBool();
              struct.setSuccessIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // CURRENT_VALUES
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list48 = iprot.readListBegin();
                struct.current_values = new java.util.ArrayList<Column>(_list48.size);
                @org.apache.thrift.annotation.Nullable Column _elem49;
                for (int _i50 = 0; _i50 < _list48.size; ++_i50)
                {
                  _elem49 = new Column();
                  _elem49.read(iprot);
                  struct.current_values.add(_elem49);
                }
                iprot.readListEnd();
              }
              struct.setCurrent_valuesIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      if (!struct.isSetSuccess()) {
        throw new org.apache.thrift.protocol.TProtocolException("Required field 'success' was not found in serialized data! Struct: " + toString());
      }
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, CASResult struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(SUCCESS_FIELD_DESC);
      oprot.writeBool(struct.success);
      oprot.writeFieldEnd();
      if (struct.current_values != null) {
        if (struct.isSetCurrent_values()) {
          oprot.writeFieldBegin(CURRENT_VALUES_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.current_values.size()));
            for (Column _iter51 : struct.current_values)
            {
              _iter51.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class CASResultTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public CASResultTupleScheme getScheme() {
      return new CASResultTupleScheme();
    }
  }

  private static class CASResultTupleScheme extends org.apache.thrift.scheme.TupleScheme<CASResult> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, CASResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      oprot.writeBool(struct.success);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetCurrent_values()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetCurrent_values()) {
        {
          oprot.writeI32(struct.current_values.size());
          for (Column _iter52 : struct.current_values)
          {
            _iter52.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, CASResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.success = iprot.readBool();
      struct.setSuccessIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        {
          org.apache.thrift.protocol.TList _list53 = iprot.readListBegin(org.apache.thrift.protocol.TType.STRUCT);
          struct.current_values = new java.util.ArrayList<Column>(_list53.size);
          @org.apache.thrift.annotation.Nullable Column _elem54;
          for (int _i55 = 0; _i55 < _list53.size; ++_i55)
          {
            _elem54 = new Column();
            _elem54.read(iprot);
            struct.current_values.add(_elem54);
          }
        }
        struct.setCurrent_valuesIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

