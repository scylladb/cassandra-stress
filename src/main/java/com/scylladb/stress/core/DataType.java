package com.scylladb.stress.core;

import java.util.List;

public class DataType {
  final private Object type;

  public DataType(com.datastax.driver.core.DataType type) {
    this.type = type;
  }

  public DataType(com.datastax.oss.driver.api.core.type.DataType type) {
    this.type = type;
  }

  public com.datastax.driver.core.DataType ToV3Value() {
    return (com.datastax.driver.core.DataType) type;
  }

  public com.datastax.oss.driver.api.core.type.DataType ToV4Value() {
    return (com.datastax.oss.driver.api.core.type.DataType) type;
  }

  public String getName() {
    if (type instanceof com.datastax.driver.core.DataType) {
      return ToV3Value().getName().name();
    }
    return ToV4Value().asCql(false, false).toUpperCase();
  }

  public boolean isFrozen() {
    if (type instanceof com.datastax.driver.core.DataType) {
      return ToV3Value().isFrozen();
    }

    if (type instanceof com.datastax.oss.driver.api.core.type.ListType) {
      return ((com.datastax.oss.driver.api.core.type.ListType)type).isFrozen();
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.SetType) {
      return ((com.datastax.oss.driver.api.core.type.SetType)type).isFrozen();
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.MapType) {
      return ((com.datastax.oss.driver.api.core.type.MapType)type).isFrozen();
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType) {
      return ((com.datastax.oss.driver.api.core.type.UserDefinedType)type).isFrozen();
    }
    return false;
  }

  public String getCollectionElementTypeName() {
    if (type instanceof com.datastax.driver.core.DataType) {
      com.datastax.driver.core.DataType casted = ToV3Value();
      if (!casted.isCollection()) {
        return "";
      }
      return ToV3Value().getTypeArguments().get(0).getName().name();
    }

    if (type instanceof com.datastax.oss.driver.api.core.type.ListType) {
      return ((com.datastax.oss.driver.api.core.type.ListType) type).getElementType().asCql(false, false);
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.SetType) {
      return ((com.datastax.oss.driver.api.core.type.SetType) type).getElementType().asCql(false, false);
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.MapType) {
      // Maps are not supported, so it should never get here.
      return ((com.datastax.oss.driver.api.core.type.MapType) type).getKeyType().asCql(false, false);
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType) {
      // Here it reproduce v3 driver behavior, it returns it empty
      return "";
    }
    return ToV4Value().asCql(false, false);
  }

  public boolean isSupported() {
    // Maps are not supported due to lack of a corresponding generator.
    // Embedded collections are not supported for the same reason.
    if  (type instanceof com.datastax.driver.core.DataType) {
      com.datastax.driver.core.DataType dataType = ToV3Value();
      if (!dataType.isCollection())
        return true;
      List<com.datastax.driver.core.DataType> arguments = dataType.getTypeArguments();
      if (arguments.size() >= 2)
        return false;
      for (com.datastax.driver.core.DataType argumentType : arguments) {
        if (argumentType.isCollection()) {
          return false;
        }
      }
      return true;
    }
    return isV4Supported(ToV4Value());
  }

  private static boolean isV4Supported(com.datastax.oss.driver.api.core.type.DataType type) {
    if (type instanceof com.datastax.oss.driver.api.core.type.ListType) {
      return isV4Supported(((com.datastax.oss.driver.api.core.type.ListType) type).getElementType());
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.SetType) {
      return isV4Supported(((com.datastax.oss.driver.api.core.type.SetType) type).getElementType());
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.MapType) {
      return isV4Supported(((com.datastax.oss.driver.api.core.type.MapType) type).getKeyType()) &&
          isV4Supported(((com.datastax.oss.driver.api.core.type.MapType) type).getValueType());
    }
    if (type instanceof com.datastax.oss.driver.api.core.type.UserDefinedType) {
      return ((com.datastax.oss.driver.api.core.type.UserDefinedType) type).getFieldTypes().stream().allMatch(DataType::isV4Supported);
    }
    return true;
  }
}
