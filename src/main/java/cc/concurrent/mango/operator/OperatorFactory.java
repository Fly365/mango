package cc.concurrent.mango.operator;

import cc.concurrent.mango.annotation.ReturnGeneratedId;
import cc.concurrent.mango.annotation.SQL;
import cc.concurrent.mango.exception.IncorrectSqlException;
import cc.concurrent.mango.exception.NoSqlAnnotationException;
import com.google.common.base.Strings;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author ash
 */
public class OperatorFactory {

    /**
     * 查询
     */
    private final static Pattern[] QUERY_PATTERNS = new Pattern[] {
            Pattern.compile("^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE), //
            Pattern.compile("^\\s*SHOW\\s+", Pattern.CASE_INSENSITIVE), //
            Pattern.compile("^\\s*DESC\\s+", Pattern.CASE_INSENSITIVE), //
            Pattern.compile("^\\s*DESCRIBE\\s+", Pattern.CASE_INSENSITIVE), //
    };

    /**
     * 插入
     */
    private final static Pattern INSERT_PATTERN = Pattern.compile("^\\s*INSERT\\s+", Pattern.CASE_INSENSITIVE);

    public static Operator getOperator(Method method) {
        SQL sqlAnno = method.getAnnotation(SQL.class);
        if (sqlAnno == null) {
            throw new NoSqlAnnotationException("need cc.concurrent.mango.annotation.SQL annotation on method");
        }
        String sql = sqlAnno.value();
        if (Strings.isNullOrEmpty(sql)) {
            throw new IncorrectSqlException("sql is null or empty");
        }

        SQLType sqlType = SQLType.WRITE;
        for (Pattern pattern : QUERY_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                sqlType = SQLType.READ;
            }
        }

        if (sqlType == SQLType.READ) {
            return new QueryOperator(method.getGenericReturnType());
        } else {
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean isBatchUpdate = false;
            if (parameterTypes.length == 1) {
                Class<?> parameterType = parameterTypes[0];
                if (Collection.class.isAssignableFrom(parameterType)) {
                    isBatchUpdate = true;
                } else if (parameterType.isArray()) {
                    isBatchUpdate = true;
                }
            }

            if (isBatchUpdate) { // 批量增删改
                return new BatchUpdateOperator(method.getReturnType());
            } else { // 单独增删改
                ReturnGeneratedId returnGeneratedIdAnno = method.getAnnotation(ReturnGeneratedId.class);
                boolean returnGeneratedId = returnGeneratedIdAnno != null // 要求返回自增id
                        && INSERT_PATTERN.matcher(sql).find(); // 是插入语句
                return new UpdateOperator(method.getReturnType(), returnGeneratedId);
            }
        }
    }

}