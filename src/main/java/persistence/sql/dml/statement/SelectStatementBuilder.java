package persistence.sql.dml.statement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import persistence.sql.dialect.ColumnType;
import persistence.sql.dml.clause.builder.FromClauseBuilder;
import persistence.sql.dml.clause.builder.WhereClauseBuilder;
import persistence.sql.dml.clause.predicate.OnPredicate;
import persistence.sql.dml.clause.predicate.WherePredicate;
import persistence.sql.exception.EntityMappingException;
import persistence.sql.exception.FieldException;
import persistence.sql.schema.meta.ColumnMeta;
import persistence.sql.schema.meta.EntityClassMappingMeta;

public class SelectStatementBuilder {

    private static final String SELECT_FORMAT = "SELECT %s";
    private static final String SELECT_ALL_FIELD = "*";
    private static final String END_OF_STATEMENT = ";";
    private static final String CLAUSE_CONCAT_FORMAT = "%s %s";

    private final StringBuilder selectStatementBuilder;
    private WhereClauseBuilder whereClauseBuilder;
    private FromClauseBuilder fromClauseBuilder;

    private SelectStatementBuilder() {
        this.selectStatementBuilder = new StringBuilder();
    }

    public static SelectStatementBuilder builder() {
        return new SelectStatementBuilder();
    }

    public SelectStatementBuilder selectFrom(EntityClassMappingMeta entityClassMappingMeta, String... targetFieldNames) {
        if (selectStatementBuilder.length() > 0) {
            throw EntityMappingException.duplicateCallMethod("select()");
        }

        validateSelectTargetField(entityClassMappingMeta, targetFieldNames);

        fromClauseBuilder = FromClauseBuilder.builder(entityClassMappingMeta.tableClause());
        if (targetFieldNames.length == 0) {
            selectStatementBuilder.append(String.format(SELECT_FORMAT, SELECT_ALL_FIELD));
            return this;
        }

        selectStatementBuilder.append(String.format(SELECT_FORMAT, String.join(", ", targetFieldNames)));
        return this;
    }

    public SelectStatementBuilder leftJoin(Class<?> clazz, OnPredicate predicate, ColumnType columnType) {
        final EntityClassMappingMeta classMappingMeta = EntityClassMappingMeta.of(clazz, columnType);
        this.fromClauseBuilder = fromClauseBuilder.innerJoin(classMappingMeta.tableClause(), predicate);
        return this;
    }

    public SelectStatementBuilder where(WherePredicate predicate) {
        this.whereClauseBuilder = WhereClauseBuilder.builder(predicate);
        return this;
    }

    public SelectStatementBuilder and(WherePredicate predicate) {
        if (this.whereClauseBuilder == null) {
            throw EntityMappingException.preconditionRequired("where()");
        }

        this.whereClauseBuilder.and(predicate);
        return this;
    }

    public SelectStatementBuilder or(WherePredicate predicate) {
        if (this.whereClauseBuilder == null) {
            throw EntityMappingException.preconditionRequired("where()");
        }

        this.whereClauseBuilder.or(predicate);
        return this;
    }

    public String build() {
        final StringBuilder selectClause = selectStatementBuilder;
        if (selectClause.length() == 0) {
            throw EntityMappingException.preconditionRequired("select()");
        }

        final String fromClause = this.fromClauseBuilder.build();
        final String selectFromClause = String.format(CLAUSE_CONCAT_FORMAT, selectClause, fromClause);

        if (this.whereClauseBuilder == null) {
            return selectFromClause + END_OF_STATEMENT;
        }

        final String whereClause = this.whereClauseBuilder.build();
        return String.format(CLAUSE_CONCAT_FORMAT, selectFromClause, whereClause) + END_OF_STATEMENT;
    }

    private void validateSelectTargetField(EntityClassMappingMeta entityClassMappingMeta, String[] targetFieldNames) {
        final List<String> definedFieldNameList = entityClassMappingMeta.getMappingColumnMetaList().stream()
            .map(ColumnMeta::getColumnName)
            .collect(Collectors.toList());

        final List<String> undefinedTargetField = Arrays.stream(targetFieldNames)
            .filter(targetFieldName -> !definedFieldNameList.contains(targetFieldName))
            .collect(Collectors.toList());

        if (!undefinedTargetField.isEmpty()) {
            throw FieldException.undefinedField(undefinedTargetField.toString());
        }
    }
}
