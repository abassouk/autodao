package gr.auth.meng.isag.autodao.internal;

import gr.auth.meng.isag.autodao.IPersistenceDAO;
import gr.auth.meng.isag.autodao.annotations.FirstResult;
import gr.auth.meng.isag.autodao.annotations.MaxResults;
import gr.auth.meng.isag.autodao.annotations.NamedQuery;
import gr.auth.meng.isag.autodao.annotations.NativeQuery;
import gr.auth.meng.isag.autodao.annotations.Param;
import gr.auth.meng.isag.autodao.annotations.Position;
import gr.auth.meng.isag.autodao.annotations.Query;
import gr.auth.meng.isag.autodao.annotations.ReturnsNull;
import gr.auth.meng.isag.autodao.annotations.Temporal;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import javax.persistence.TemporalType;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.Type;

public abstract class DAOAnalysis
        extends BaseClassGenAnalysis<IPersistenceDAO>
        implements Constants {
    protected static final String NO_RESULT_EXCEPTION =
        "javax.persistence.NoResultException";

    protected static final String EM_TYPE = "javax.persistence.EntityManager";

    protected static final String INT_CLASS = "java.lang.Integer";

    protected static final String Q_CLASS = "javax.persistence.Query";

    protected static final String TQ_CLASS = "javax.persistence.TypedQuery";

    protected static final ObjectType TEMPORAL_TYPE = new ObjectType(
            "javax.persistence.TemporalType");

    protected static final Set<String> noArgEMMethods = new HashSet<String>(
            Arrays.asList("flush", "clear"));

    protected static final Set<String> passThroughMethods =
        new HashSet<String>(Arrays.asList("detach", "persist", "refresh",
            "remove"));

    protected static final Set<String> transactionMethods =
        new HashSet<String>(Arrays.asList("commit", "begin", "rollback"));

    public DAOAnalysis(Class<? extends IPersistenceDAO> itemClass) {
        super(itemClass, IPersistenceDAO.class);
    }

    protected void createInheritedConstructor(Class<?> parameterType) {
        Type param = getTypeForClass(parameterType);
        GeneratedMethod gm =
            new GeneratedMethod(ACC_PUBLIC, Type.VOID, "<init>",
                    new Type[] { param });
        InstructionList il = gm.start();

        boolean doInherited = !originalClass.isInterface();
        if (doInherited) {
            try {
                doInherited =
                    originalClass.getConstructor(parameterType) != null;
            } catch (Exception e) {
                doInherited = false;
            }
        }

        il.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        if (doInherited) {
            il.append(InstructionFactory.createLoad(Type.OBJECT, 1));
            il.append(_factory.createInvoke(originalClass.getName(), "<init>",
                Type.VOID, new Type[] { param }, Constants.INVOKESPECIAL));
        } else {
            il.append(_factory.createInvoke(
                originalClass.isInterface() ? "java.lang.Object"
                        : originalClass.getName(), "<init>", Type.VOID,
                Type.NO_ARGS, Constants.INVOKESPECIAL));
        }
        il.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        il.append(InstructionFactory.createLoad(Type.OBJECT, 1));
        il.append(_factory.createFieldAccess(targetClassName, "em", param,
            Constants.PUTFIELD));

        il.append(InstructionFactory.createReturn(Type.VOID));

        gm.done();
    }

    @Override
    protected List<Method> findCandidateMethods() {
        List<Method> ms = super.findCandidateMethods();
        for (Iterator<Method> iterator = ms.iterator(); iterator.hasNext();) {
            Method m = iterator.next();
            if (m.getName().equals("getEntityManager")
                    && m.getParameterTypes().length == 0
                    && m.getReturnType() == EntityManager.class)
                iterator.remove();
        }
        return ms;
    }

    protected void processMethod(ParsedMethod pm) {
        // a: check if it's a query method
        if (pm.getAnnotation(Query.class) != null
                || pm.getAnnotation(NamedQuery.class) != null
                || pm.getAnnotation(NativeQuery.class) != null) {
            doQueryMethod(pm);
            return;
        }

        String n = pm.getMethod().getName();

        // b: no-arg transaction or EntityManager method
        if (pm.getMethod().getParameterTypes().length == 0) {
            if (transactionMethods.contains(n)) {
                addTransactionMethod(pm);
                return;
            }
            if (noArgEMMethods.contains(n)) {
                addPassThruMethod(pm);
                return;
            }
        }
        // c: only 1-arg methods from here on.
        if (pm.getArgumentLength() != 1) {
            throw new IllegalArgumentException("Non-Query Method "
                    + pm.getMethod() + " has invalid signature.");
        }
        // find By PK methods
        if (n.startsWith("find")) {
            addFindMethod(pm);
            return;
        }
        // <T> T merge(T) methods, with T concrete entity
        if ("merge".equals(n)) {
            addPassThruMethod(pm);
            return;
        }
        // 1-arg entity EM methods - delete, refresh, persist
        if (passThroughMethods.contains(n)) {
            addPassThruMethod(pm);
            return;
        }
        // nothing? it's wrong(TM).
        throw new IllegalArgumentException("Method " + pm.getMethod()
                + " is abstract but cannot be converted.");
    }

    protected void doQueryMethod(ParsedMethod m) {
        Query qAnn = m.getAnnotation(Query.class);
        NamedQuery nqAnn = m.getAnnotation(NamedQuery.class);
        NativeQuery ntAnn = m.getAnnotation(NativeQuery.class);

        Class<?> returnType = m.getMethod().getReturnType();
        if (returnType != Void.TYPE && returnType.isPrimitive()) {
            throw new IllegalArgumentException("Primitive return type of " + m);
        }

        GeneratedMethod gm = new GeneratedMethod(m);
        ReferenceType paramType = determineQueryType(m.getMethod(), returnType);

        InstructionList il = gm.start();

        // getEntityManager()
        writeMethodPreamble(gm, il);

        // createQuery()/createNamedQuery();
        il.append(new PUSH(_cp, qAnn != null ? qAnn.value()
                : (nqAnn != null ? nqAnn.value() : ntAnn.value())));
        if (paramType != null)
            il.append(new PUSH(_cp, paramType));

        String queryType = paramType != null ? TQ_CLASS : Q_CLASS;

        il.append(_factory.createInvoke(EM_TYPE, qAnn != null ? "createQuery"
                : (nqAnn != null ? "createNamedQuery" : "createNativeQuery"),
            new ObjectType(queryType), paramType != null ? new Type[] {
                    Type.STRING, Type.CLASS } : new Type[] { Type.STRING },
            Constants.INVOKEINTERFACE));

        // setFirstResult()/setMaxResults()
        processMethodAnnotations(m, il);
        // setParameter()
        processParameters(m, il, queryType);
        if (qAnn != null) {
            addLockModeOps(il, qAnn.lockMode(), qAnn.hints());
        } else if (ntAnn != null) {
            addLockModeOps(il, null, ntAnn.hints());
        }

        if (returnType == Void.TYPE || returnType == Integer.TYPE) {
            il.append(_factory.createInvoke(queryType, "executeUpdate",
                Type.INT, Type.NO_ARGS, Constants.INVOKEINTERFACE));
            if (returnType == Void.TYPE)
                il.append(InstructionFactory.createPop(1));
            il.append(InstructionFactory.createReturn(Type.VOID));
        } else if (List.class.isAssignableFrom(returnType)) {
            il.append(_factory.createInvoke(queryType, "getResultList",
                new ObjectType("java.util.List"), Type.NO_ARGS,
                Constants.INVOKEINTERFACE));
            il.append(InstructionFactory.createReturn(Type.OBJECT));
        } else {
            il.append(_factory.createInvoke(queryType, "getSingleResult",
                Type.OBJECT, Type.NO_ARGS, Constants.INVOKEINTERFACE));
            il.append(_factory.createCheckCast(new ObjectType(returnType
                    .getName())));
            il.append(InstructionFactory.createReturn(Type.OBJECT));
        }

        // check to see if we should handle NoResultException
        if (m.getAnnotation(ReturnsNull.class) != null) {
            InstructionHandle start = il.getStart();
            InstructionHandle end = il.getEnd();

            InstructionHandle handler =
                il.append(new org.apache.bcel.generic.POP());
            il.append(InstructionFactory.ACONST_NULL);
            il.append(InstructionFactory.createReturn(Type.OBJECT));
            gm.getMethod().addExceptionHandler(start, end, handler,
                new ObjectType(NO_RESULT_EXCEPTION));

        }
        gm.done();
    }

    protected void addLockModeOps(InstructionList il, LockModeType lockMode,
            QueryHint[] hints) {
        // setQueryHints()
        processQueryHints(hints, il, TQ_CLASS);
        // setLockMode()
        if (lockMode != null && lockMode != LockModeType.NONE) {
            ObjectType lm = new ObjectType("javax.persistence.LockModeType");
            il.append(_factory.createFieldAccess(
                "javax.persistence.LockModeType", lockMode.name(), lm,
                Constants.GETSTATIC));
            il.append(_factory.createInvoke(TQ_CLASS, "setLockMode",
                new ObjectType(TQ_CLASS), new Type[] { lm },
                Constants.INVOKEINTERFACE));
        }
    }

    protected void addTransactionMethod(ParsedMethod pm) {
        String et = EntityTransaction.class.getCanonicalName();

        GeneratedMethod gm = new GeneratedMethod(pm);
        InstructionList il = gm.start();

        writeMethodPreamble(gm, il);
        il.append(_factory.createInvoke(EM_TYPE, "getTransaction",
            new ObjectType(et), Type.NO_ARGS, Constants.INVOKEINTERFACE));
        il.append(_factory.createInvoke(et, pm.getMethod().getName(),
            Type.VOID, Type.NO_ARGS, Constants.INVOKEINTERFACE));

        il.append(InstructionFactory.createReturn(Type.VOID));

        gm.done();
    }

    protected void addPassThruMethod(ParsedMethod m) {
        GeneratedMethod gm = new GeneratedMethod(m);
        InstructionList il = gm.start();
        Type returnType = gm.getReturnType();

        boolean hasArg = m.getArgumentLength() > 0;

        writeMethodPreamble(gm, il);
        if (hasArg)
            il.append(InstructionFactory.createLoad(Type.OBJECT, 1));
        il.append(_factory.createInvoke(EM_TYPE, m.getMethod().getName(),
            returnType == Type.VOID ? Type.VOID : Type.OBJECT,
            hasArg ? new Type[] { Type.OBJECT } : Type.NO_ARGS,
            Constants.INVOKEINTERFACE));

        if (returnType != Type.VOID) {
            il.append(_factory.createCheckCast(((ReferenceType) returnType)));
        }
        il.append(InstructionFactory.createReturn(returnType));

        gm.done();
    }

    protected void addFindMethod(ParsedMethod m) {
        GeneratedMethod gm = new GeneratedMethod(m);
        InstructionList il = gm.start();

        writeMethodPreamble(gm, il);
        il.append(new PUSH(_cp, (ObjectType) gm.getReturnType()));

        m.getArguments()[0].pushAsObject(il);

        il.append(_factory.createInvoke(EM_TYPE, "find", Type.OBJECT,
            new Type[] { Type.CLASS, Type.OBJECT }, Constants.INVOKEINTERFACE));

        il.append(_factory.createCheckCast(((ReferenceType) gm.getReturnType())));
        il.append(InstructionFactory.createReturn(gm.getReturnType()));

        gm.done();
    }

    protected abstract void writeMethodPreamble(GeneratedMethod method,
            InstructionList il);

    protected abstract void writeMethodEpilogue(GeneratedMethod method,
            InstructionList il);

    protected void processMethodAnnotations(ParsedMethod m, InstructionList il) {
        MaxResults mr = m.getMethod().getAnnotation(MaxResults.class);
        if (mr != null) {
            if (mr.value() < 0) {
                throw new IllegalArgumentException(
                        "MaxResults without a value on " + m.getMethod());
            }
            il.append(new PUSH(_cp, mr.value()));
            il.append(_factory.createInvoke(TQ_CLASS, "setMaxResults",
                new ObjectType(TQ_CLASS), new Type[] { Type.INT },
                Constants.INVOKEINTERFACE));
        }

        FirstResult fr = m.getMethod().getAnnotation(FirstResult.class);
        if (fr != null && fr.value() > -1) {
            il.append(new PUSH(_cp, fr.value()));
            il.append(_factory.createInvoke(TQ_CLASS, "setFirstResult",
                new ObjectType(TQ_CLASS), new Type[] { Type.INT },
                Constants.INVOKEINTERFACE));
        }
    }

    // horrible stuff going on here... I feel unclean.
    protected ReferenceType determineQueryType(Method m, Class<?> returnType) {
        if (Collection.class.isAssignableFrom(returnType)) {
            java.lang.reflect.Type retType = m.getGenericReturnType();

            if (retType == null || !(retType instanceof ParameterizedType))
                return null;
            java.lang.reflect.Type[] args =
                ((ParameterizedType) retType).getActualTypeArguments();
            if (args.length != 1)
                return null;
            java.lang.reflect.Type at = args[0];
            while (at instanceof ParameterizedType) {
                at = ((ParameterizedType) at).getRawType();
            }

            if (at instanceof Class<?>) {
                int num = 0;
                while (((Class<?>) at).isArray()) {
                    num++;
                    at = ((Class<?>) at).getComponentType();
                }
                if (num > 0) {
                    return new ArrayType(((Class<?>) at).getCanonicalName(),
                            num);
                }
                return new ObjectType(((Class<?>) at).getCanonicalName());
            }
            if (at instanceof GenericArrayType) {
                int num = 0;
                while (at instanceof GenericArrayType) {
                    num++;
                    at = ((GenericArrayType) at).getGenericComponentType();
                    while (at instanceof ParameterizedType) {
                        at = ((ParameterizedType) at).getRawType();
                    }
                }
                return new ArrayType(getTypeForClass(((Class<?>) at)), num);
            }
            return null;
        } else if (returnType == Void.TYPE) {
            return null;
        } else {
            return (ReferenceType) getTypeForClass(returnType);
        }
    }

    protected void processParameters(ParsedMethod pm, InstructionList il,
            String queryClass) {
        for (int i = 0; i < pm.getArgumentLength(); i++) {
            processParameter(pm, pm.getArguments()[i], il, queryClass);
        }
    }

    protected void processParameter(ParsedMethod m, Argument arg,
            InstructionList il, String queryClass) {
        Param param = null;
        Position pos = null;
        String tempType = null;
        for (Annotation a : arg.getAnnotations()) {
            if (a instanceof Temporal) {
                Temporal t = ((Temporal) a);
                tempType = t.value().name();
                continue;
            } else if (a instanceof Param) {
                param = (Param) a;
                break;
            } else if (a instanceof Position) {
                pos = (Position) a;
                break;
            } else if (a instanceof MaxResults) {
                processQuerySetOperation(m, arg, il, "setMaxResults",
                    queryClass);
                return;
            } else if (a instanceof FirstResult) {
                processQuerySetOperation(m, arg, il, "setFirstResult",
                    queryClass);
                return;
            }
        }

        if ((param == null) == (pos == null)) {
            throw new IllegalArgumentException("Unbound parameter #"
                    + arg.getArgNo() + " in " + m);
        }

        int maxRep = 1;
        if (pos != null) {
            maxRep = pos.value().length;
        }
        for (int j = 0; j < maxRep; j++) {
            Type parmType;
            if (pos != null) {
                il.append(new PUSH(_cp, pos.value()[j]));
                parmType = Type.INT;
            } else {
                il.append(new PUSH(_cp, param.value()));
                parmType = Type.STRING;
            }
            Class<?> type = arg.getArgumentClass();
            if (type == Calendar.class || type == Date.class) {
                if (tempType == null)
                    tempType = TemporalType.TIMESTAMP.name();
            }
            if (tempType != null
                    && (type != Calendar.class && type != Date.class))
                throw new IllegalArgumentException(
                        "Temporal annotation on non-Date, non-Calendar parameter on "
                                + m);
            arg.pushAsObject(il);
            if (tempType == null) {
                il.append(_factory.createInvoke(queryClass, "setParameter",
                    new ObjectType(queryClass), new Type[] { parmType,
                            Type.OBJECT }, Constants.INVOKEINTERFACE));
            } else {
                il.append(_factory.createFieldAccess(
                    "javax.persistence.TemporalType", tempType, TEMPORAL_TYPE,
                    Constants.GETSTATIC));
                il.append(_factory.createInvoke(queryClass, "setParameter",
                    new ObjectType(queryClass),
                    new Type[] { parmType, arg.getType(), TEMPORAL_TYPE },
                    Constants.INVOKEINTERFACE));
            }
        }
    }

    protected void processQuerySetOperation(ParsedMethod m, Argument arg,
            InstructionList il, String methodName, String queryClass) {
        Class<?> type = arg.getArgumentClass();
        if (type == Integer.class) {
            arg.push(il);
            il.append(_factory.createInvoke(INT_CLASS, "intValue", Type.INT,
                Type.NO_ARGS, Constants.INVOKEINTERFACE));
        } else if (type == int.class) {
            arg.pushPrimitive(il);
        } else {
            throw new IllegalArgumentException("Parameter #" + arg.getArgNo()
                    + " of " + m.getMethod() + " needs to be Integer or int.");
        }
        il.append(_factory
                .createInvoke(queryClass, methodName,
                    new ObjectType(queryClass), new Type[] { Type.INT },
                    Constants.INVOKEINTERFACE));
    }

    protected void processQueryHints(QueryHint[] hints, InstructionList il,
            String queryClass) {
        for (QueryHint qh : hints) {
            il.append(new PUSH(_cp, qh.name()));
            il.append(new PUSH(_cp, qh.value()));
            il.append(_factory.createInvoke(queryClass, "setQueryHint",
                new ObjectType(queryClass), new Type[] { Type.STRING,
                        Type.OBJECT }, Constants.INVOKEINTERFACE));
        }
    }
}
