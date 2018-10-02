package org.col.es.mapping;

import static org.col.es.mapping.ESDataType.KEYWORD;
import static org.col.es.mapping.ESDataType.NESTED;
import static org.col.es.mapping.MappingUtil.extractProperty;
import static org.col.es.mapping.MappingUtil.getClassForTypeArgument;
import static org.col.es.mapping.MappingUtil.getFields;
import static org.col.es.mapping.MappingUtil.getMappedProperties;
import static org.col.es.mapping.MultiField.DEFAULT_MULTIFIELD;
import static org.col.es.mapping.MultiField.CI_MULTIFIELD;
import static org.col.es.mapping.MultiField.NGRAM0_MULTIFIELD;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.col.api.annotations.Analyzer;
import org.col.api.annotations.Analyzers;
import org.col.api.annotations.NotIndexed;
import org.col.api.annotations.NotNested;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generates Elasticsearch type mappings from {@link Class} objects. For each instance field in the
 * Java class a counterpart will be created in the Elasticsearch document type, unless it is
 * annotated with {@link JsonIgnore}. Getters are ignored unless they are annotated with
 * {@link JsonProperty}.
 */
public class MappingFactory<T> {

  private static final HashMap<Class<?>, Mapping<?>> cache = new HashMap<>();

  /**
   * Creates a document type mapping for the specified class.
   * 
   * @param type
   * @return
   */
  public Mapping<T> getMapping(Class<T> type) {
    @SuppressWarnings("unchecked")
    Mapping<T> mapping = (Mapping<T>) cache.get(type);
    if (mapping == null) {
      mapping = new Mapping<>(type);
      addFieldsToDocument(mapping, type, newTree(new HashSet<>(0), type));
      cache.put(type, mapping);
    }
    return mapping;
  }

  private static void addFieldsToDocument(ComplexField document, Class<?> type,
      HashSet<Class<?>> ancestors) {
    for (Field javaField : getFields(type)) {
      //System.out.println("Mapping field " + javaField.getName());
      ESField esField = createESField(javaField, ancestors);
      esField.setName(javaField.getName());
      esField.setParent(document);
      esField.setArray(isMultiValued(javaField));
      document.addField(javaField.getName(), esField);
    }
    for (Method javaMethod : getMappedProperties(type)) {
      //System.out.println("Mapping method " + javaMethod);
      String methodName = javaMethod.getName();
      String fieldName = extractProperty(methodName);
      ESField esField = createESField(javaMethod, ancestors);
      esField.setName(fieldName);
      esField.setParent(document);
      esField.setArray(isMultiValued(javaMethod));
      document.addField(fieldName, esField);
    }
  }

  private static ESField createESField(Field field, HashSet<Class<?>> ancestors) {
    Class<?> realType = field.getType();
    Class<?> mapToType = mapType(realType, field.getGenericType());
    ESDataType esType = DataTypeMap.INSTANCE.getESType(mapToType);
    if (esType == null) {
      /*
       * Then the Java type does not map to a simple Elasticsearch type; the Elastichsearch type is
       * either "object" or "nested".
       */
      if (ancestors.contains(mapToType)) {
        throw new ClassCircularityException(field, mapToType);
      }
      return createDocument(field, mapToType, newTree(ancestors, mapToType));
    }
    boolean forEnum = isOrContainsEnum(realType, field.getGenericType());
    return createSimpleField(field, esType, forEnum);
  }

  private static ESField createESField(Method method, HashSet<Class<?>> ancestors) {
    Class<?> realType = method.getReturnType();
    Class<?> mapToType = mapType(realType, method.getGenericReturnType());
    ESDataType esType = DataTypeMap.INSTANCE.getESType(mapToType);
    if (esType == null) {
      if (ancestors.contains(mapToType)) {
        throw new ClassCircularityException(method, mapToType);
      }
      return createDocument(method, mapToType, newTree(ancestors, mapToType));
    }
    boolean forEnum = isOrContainsEnum(realType, method.getGenericReturnType());
    return createSimpleField(method, esType, forEnum);
  }

  private static SimpleField createSimpleField(AnnotatedElement fm, ESDataType esType,
      boolean isEnum) {
    SimpleField sf;
    switch (esType) {
      case KEYWORD:
        sf = new KeywordField();
        break;
      case DATE:
        sf = new DateField();
        break;
      default:
        sf = new SimpleField(esType);
    }
    if (fm.getAnnotation(NotIndexed.class) == null) {
      if (esType == KEYWORD) {
        addMultiFields((KeywordField) sf, fm, isEnum);
      }
    } else {
      sf.setIndex(Boolean.FALSE);
    }
    return sf;
  }

  private static ComplexField createDocument(Field field, Class<?> mapToType,
      HashSet<Class<?>> ancestors) {
    Class<?> realType = field.getType();
    ComplexField document;
    if (realType.isArray() || isA(realType, Collection.class)) {
      if (field.getAnnotation(NotNested.class) == null) {
        document = new ComplexField(NESTED);
      } else {
        document = new ComplexField();
      }
    } else {
      document = new ComplexField();
    }
    addFieldsToDocument(document, mapToType, ancestors);
    return document;
  }

  private static ComplexField createDocument(Method method, Class<?> mapToType,
      HashSet<Class<?>> ancestors) {
    Class<?> realType = method.getReturnType();
    ComplexField document;
    if (realType.isArray() || isA(realType, Collection.class)) {
      if (method.getAnnotation(NotNested.class) == null) {
        document = new ComplexField(NESTED);
      } else {
        document = new ComplexField();
      }
    } else {
      document = new ComplexField();
    }
    addFieldsToDocument(document, mapToType, ancestors);
    return document;
  }

  /*
   * Returns false if the Java field does not contain the @Analyzers annotation. Otherwise it
   * returns true.
   */
  private static boolean addMultiFields(KeywordField kf, AnnotatedElement fm, boolean forEnum) {
    Analyzers annotation = fm.getAnnotation(Analyzers.class);
    if (annotation == null) {
      kf.addMultiField(CI_MULTIFIELD);
      if (!forEnum) {
        kf.addMultiField(DEFAULT_MULTIFIELD);
      }
      return false;
    }
    if (annotation.value().length == 0) {
      return true;
    }
    List<Analyzer> value = Arrays.asList(annotation.value());
    EnumSet<Analyzer> analyzers = EnumSet.copyOf(value);
    for (Analyzer a : analyzers) {
      switch (a) {
        case CASE_INSENSITIVE:
          kf.addMultiField(CI_MULTIFIELD);
          break;
        case DEFAULT:
          kf.addMultiField(DEFAULT_MULTIFIELD);
          break;
        case NGRAM0:
          kf.addMultiField(NGRAM0_MULTIFIELD);
          break;
        default:
          break;
      }
    }
    return true;
  }

  /*
   * Maps a Java class to another Java class that must be used in its place. The latter class is
   * then mapped to an Elasticsearch data type. When mapping arrays, it's not the array that is
   * mapped but the class of its elements. No array type exists or is required in Elasticsearch;
   * fields are intrinsically multi-valued.
   */
  private static Class<?> mapType(Class<?> type, Type typeArg) {
    if (type.isArray())
      return type.getComponentType();
    if (isA(type, Collection.class))
      return getClassForTypeArgument(typeArg);
    if (type.isEnum())
      return String.class;
    return type;
  }

  /*
   * Whether or not the specified type is an enum or an array/collection of enums. If so the
   * corresponding Elasticsearch field will not be indexed for full-text search.
   */
  private static boolean isOrContainsEnum(Class<?> type, Type typeArg) {
    if (type.isEnum()) {
      return true;
    }
    if (type.isArray()) {
      return type.getComponentType().isEnum();
    }
    if (isA(type, Collection.class)) {
      return getClassForTypeArgument(typeArg).isEnum();
    }
    return false;
  }

  private static boolean isMultiValued(Field f) {
    if (f.getType().isArray())
      return true;
    if (isA(f.getType(), Collection.class))
      return true;
    return false;
  }

  private static boolean isMultiValued(Method m) {
    if (m.getReturnType().isArray())
      return true;
    if (isA(m.getReturnType(), Collection.class))
      return true;
    return false;
  }

  private static HashSet<Class<?>> newTree(HashSet<Class<?>> ancestors, Class<?> newType) {
    HashSet<Class<?>> set = new HashSet<>();
    set.addAll(ancestors);
    set.add(newType);
    return set;
  }

  private static boolean isA(Class<?> cls, Class<?> interfaceOrSuperClass) {
    return interfaceOrSuperClass.isAssignableFrom(cls);
  }

}