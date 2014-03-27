package gr.auth.meng.isag.autodao.annotations;

import static javax.persistence.LockModeType.NONE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Query {
    String value();

    QueryHint[] hints() default {};
    
    LockModeType lockMode() default NONE;
}
