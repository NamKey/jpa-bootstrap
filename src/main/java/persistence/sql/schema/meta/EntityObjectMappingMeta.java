package persistence.sql.schema.meta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import persistence.entity.impl.EntityIdentifier;
import persistence.sql.dialect.ColumnType;
import persistence.sql.dialect.H2ColumnType;
import persistence.sql.exception.ClassMappingException;
import persistence.sql.exception.FieldException;

public class EntityObjectMappingMeta {

    private final Map<ColumnMeta, ValueMeta> objectValueMap = new LinkedHashMap<>();
    private final EntityClassMappingMeta entityClassMappingMeta;
    private final EntityIdentifier entityIdentifier;

    private EntityObjectMappingMeta(
        EntityClassMappingMeta entityClassMappingMeta, EntityIdentifier entityIdentifier, Map<ColumnMeta, ValueMeta> valueMap
    ) {
        this.entityClassMappingMeta = entityClassMappingMeta;
        this.entityIdentifier = entityIdentifier;
        this.objectValueMap.putAll(valueMap);
    }

    public static EntityObjectMappingMeta of(Object instance, ColumnType columnType) {
        final Map<ColumnMeta, ValueMeta> valueMap = new LinkedHashMap<>();

        final EntityClassMappingMeta entityClassMappingMeta = EntityClassMappingMeta.of(instance.getClass(), columnType);

        entityClassMappingMeta.getMappingFieldList().forEach(field ->
            valueMap.put(entityClassMappingMeta.getColumnMeta(field), getFieldValueAsObject(field, instance))
        );

        final EntityIdentifier entityIdentifier = initEntityIdentifier(new ArrayList<>(valueMap.entrySet()));

        return new EntityObjectMappingMeta(entityClassMappingMeta, entityIdentifier, valueMap);
    }

    public List<ColumnMeta> getColumnMetaList() {
        return new ArrayList<>(objectValueMap.keySet());
    }

    public List<Entry<ColumnMeta, ValueMeta>> getMetaEntryList() {
        return new ArrayList<>(objectValueMap.entrySet());
    }

    public Map<String, Object> getValueMapByColumnName() {
        Map<String, Object> map = new HashMap<>();
        for (Entry<ColumnMeta, ValueMeta> entry : objectValueMap.entrySet()) {
            map.put(entry.getKey().getColumnName(), entry.getValue().getValue());
        }

        return map;
    }

    public String getIdColumnName() {
        final ColumnMeta idColumnMeta = objectValueMap.keySet().stream()
            .filter(ColumnMeta::isPrimaryKey)
            .findAny()
            .orElseThrow(() -> ClassMappingException.columnNotFound("Id"));

        return idColumnMeta.getColumnName();
    }

    public Object getIdValue() {
        final ValueMeta idValueMeta = objectValueMap.entrySet().stream()
            .filter(entry -> entry.getKey().isPrimaryKey())
            .map(Entry::getValue)
            .findAny()
            .orElseThrow(() -> ClassMappingException.columnNotFound("Id"));

        return idValueMeta.getValue();
    }

    public static <T> boolean isNew(T t) {
        final EntityObjectMappingMeta objectMappingMeta = EntityObjectMappingMeta.of(t, new H2ColumnType());

        return objectMappingMeta.getIdValue() == null;
    }

    private static ValueMeta getFieldValueAsObject(Field field, Object object) {
        field.setAccessible(true);
        try {
            return ValueMeta.of(field.get(object));
        } catch (IllegalAccessException e) {
            throw FieldException.accessRequire(field.getName());
        }
    }

    private static EntityIdentifier initEntityIdentifier(
        List<Entry<ColumnMeta, ValueMeta>> valueMapEntry
    ) {
        return valueMapEntry.stream()
            .filter(entry -> entry.getKey().isPrimaryKey())
            .map(entry -> EntityIdentifier.fromIdColumnMetaWithValueMeta(entry.getKey(), entry.getValue()))
            .findAny()
            .orElseThrow(() -> ClassMappingException.columnNotFound("Id"));
    }

    public EntityIdentifier getEntityIdentifier() {
        return entityIdentifier;
    }

    public Object tableClause() {
        return entityClassMappingMeta.tableClause();
    }

    public EntityClassMappingMeta getEntityClassMappingMeta() {
        return entityClassMappingMeta;
    }

    public List<Entry<ColumnMeta, ValueMeta>> getDifferMetaEntryList(EntityObjectMappingMeta objectMappingMeta) {
        return objectValueMap.entrySet().stream()
            .filter(entry -> !objectMappingMeta.getMetaEntryList().contains(entry))
            .collect(Collectors.toList());
    }
}
