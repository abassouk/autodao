package gr.auth.meng.isag.autodao.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

public abstract class BaseClassGenAnalysis<T>
        implements Constants {
    protected class Argument {
        private String name;

        private int argNo;

        private int soffset;

        private int slen;

        private Type type;

        private Class<?> klass;

        private Annotation[] annotations;

        protected Argument(Method m, int pos, Argument prev) {
            this.klass = m.getParameterTypes()[pos];
            this.type = getTypeForClass(klass);
            this.annotations = m.getParameterAnnotations()[pos];
            this.argNo = pos;
            if (prev != null) {
                this.soffset = prev.slen + prev.soffset;
            } else {
                this.soffset = 1;
            }
            if (klass == Long.TYPE || klass == Double.TYPE) {
                slen = 2;
            } else {
                slen = 1;
            }
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public Class<?> getArgumentClass() {
            return klass;
        }

        public Annotation[] getAnnotations() {
            return annotations;
        }

        public void pushAsObject(InstructionList il) {
            if (klass.isPrimitive()) {
                pushPrimitiveAsObject(il);
            } else {
                il.append(InstructionFactory.createLoad(Type.OBJECT, soffset));
            }
        }

        public void pushPrimitiveAsObject(InstructionList il) {
            Type target = getTypeForPrimitive(klass);
            String boxedType = getBoxedType(klass).getCanonicalName();
            il.append(InstructionFactory.createLoad(target, soffset));
            il.append(_factory.createInvoke(boxedType, "valueOf",
                new ObjectType(boxedType), new Type[] { target },
                Constants.INVOKESTATIC));
        }

        public void pushPrimitive(InstructionList il) {
            Type target = getTypeForPrimitive(klass);
            il.append(InstructionFactory.createLoad(target, soffset));
        }

        public void push(InstructionList il) {
            if (klass.isPrimitive()) {
                pushPrimitive(il);
            } else {
                il.append(InstructionFactory.createLoad(Type.OBJECT, soffset));
            }
        }

        public int getArgNo() {
            return argNo;
        }

        public int getOffset() {
            return soffset;
        }
    }

    protected class ParsedMethod {
        private Argument[] arguments;

        private Annotation[] annotations;

        private Method method;

        @SuppressWarnings("unchecked")
        public ParsedMethod(Method m) {
            this.method = m;
            this.annotations = m.getAnnotations();

            Class<?>[] parameterTypes = m.getParameterTypes();
            arguments =
                new BaseClassGenAnalysis.Argument[parameterTypes.length];
            Argument lastArg = null;
            for (int i = 0; i < arguments.length; i++) {
                lastArg = arguments[i] = new Argument(m, i, lastArg);
            }
        }

        public Type[] getArgumentTypes() {
            Type[] rvs = new Type[arguments.length];
            for (int i = 0; i < rvs.length; i++) {
                rvs[i] = arguments[i].getType();
            }
            return rvs;
        }

        public Argument[] getArguments() {
            return arguments;
        }

        public Annotation[] getAnnotations() {
            return annotations;
        }

        public Method getMethod() {
            return method;
        }

        public int getArgumentLength() {
            return arguments.length;
        }

        public <A extends Annotation> A getAnnotation(Class<A> class1) {
            return getMethod().getAnnotation(class1);
        }
    }

    protected class GeneratedMethod {
        private MethodGen method;

        private InstructionList il = new InstructionList();

        private Type return_type;

        public GeneratedMethod(int access_flags, Type return_type,
                String method_name, Type[] arg_types) {
            String[] arg_names = getArgNames(arg_types.length);
            method =
                new MethodGen(access_flags, this.return_type = return_type,
                        arg_types, arg_names, method_name, targetClassName, il,
                        _cp);
        }

        public void unsetAccessFlags(int accessFlags) {
            method.setAccessFlags(method.getAccessFlags() & ~accessFlags);
        }

        public void setAccessFlags(int accessFlags) {
            method.setAccessFlags(method.getAccessFlags() | accessFlags);
        }

        private String[] getArgNames(int length) {
            String[] arg_names = new String[length];
            for (int i = 0; i < length; i++) {
                arg_names[i] = "arg" + i;
            }
            return arg_names;
        }

        public GeneratedMethod(ParsedMethod pm) {
            Method m = pm.getMethod();
            this.return_type = getTypeForClass(m.getReturnType());
            method =
                new MethodGen(m.getModifiers() & ~Modifier.ABSTRACT,
                        this.return_type, pm.getArgumentTypes(),
                        getArgNames(pm.getArgumentLength()), m.getName(),
                        targetClassName, il, _cp);
        }

        public MethodGen getMethod() {
            return method;
        }

        public InstructionList start() {
            return this.il = method.getInstructionList();
        }

        public void done() {
            method.setMaxStack();
            method.setMaxLocals();
            _cg.addMethod(method.getMethod());
            il.dispose();
        }

        public Type getReturnType() {
            return return_type;
        }

    }

    private static Method defineClassMethod;

    private static Method findClassMethod;

    private boolean debug;

    // ////////////////////////
    public BaseClassGenAnalysis(Class<? extends T> itemClass,
            Class<T> interfaceClass) {
        this.originalClass = itemClass;
        this.interfaceClass = interfaceClass;
    }

    protected Class<T> interfaceClass;

    protected Class<?> originalClass;

    protected Class<? extends T> targetClass;

    protected String targetClassName;

    protected Constructor<? extends T> defConstructor;

    protected ConstantPoolGen _cp;

    protected ClassGen _cg;

    protected InstructionFactory _factory;

    private byte[] classFile;

    protected void buildTargetClassName() {
        String name = originalClass.getName();
        targetClassName = name + getNameSuffix();
    }

    protected abstract String getNameSuffix();

    public void commence()
            throws Exception {
        String name = originalClass.getName();
        buildTargetClassName();

        if (tryFindTargetClass(originalClass.getClassLoader(), targetClassName))
            return;

        if (originalClass.isInterface()) {
            _cg =
                new ClassGen(targetClassName, "java.lang.Object", name
                        + "-impl.java", ACC_PUBLIC | ACC_SUPER, new String[] {
                        name, interfaceClass.getCanonicalName() });
        } else {
            _cg =
                new ClassGen(targetClassName, name, name + "-impl.java",
                        ACC_PUBLIC | ACC_SUPER,
                        new String[] { interfaceClass.getCanonicalName() });
        }
        _cg.setMajor(MAJOR_1_5);
        _cg.setMinor(MINOR_1_5);

        _cp = _cg.getConstantPool();
        _factory = new InstructionFactory(_cg, _cp);
        setUpClassfile();
        List<Method> methods = findCandidateMethods();
        for (Method m : methods) {
            processMethod(new ParsedMethod(m));
        }
        defineClass();
        cleanup();

        if (!tryFindTargetClass(originalClass.getClassLoader(), targetClassName))
            throw new IllegalStateException(
                    "Could not find new class after analysis");
    }

    protected abstract void processMethod(ParsedMethod parsedMethod);

    protected abstract void setUpClassfile()
            throws Exception;

    @SuppressWarnings("unchecked")
    protected byte[] defineClass()
            throws Exception {
        byte[] array = buildClassFile();

        Method defineClassMethod = getDefineClassMethod();
        Object object =
            defineClassMethod.invoke(originalClass.getClassLoader(),
                targetClassName, array, 0, array.length);
        targetClass = (Class<? extends T>) object;
        if (debug) {
            this.classFile = array;
            try {
                FileOutputStream f =
                    new FileOutputStream("/tmp/" + _cg.getClassName()
                            + ".class");
                f.write(array);
                f.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        return array;
    }

    protected byte[] buildClassFile()
            throws IOException {
        byte[] array = null;
        JavaClass javaClass = _cg.getJavaClass();
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        javaClass.dump(bas);
        array = bas.toByteArray();
        bas = null;
        return array;
    }

    protected void cleanup() {
        _cp = null;
        _cg = null;
        _factory = null;
    }

    protected Method getDefineClassMethod()
            throws NoSuchMethodException {
        if (defineClassMethod == null) {
            defineClassMethod =
                ClassLoader.class.getDeclaredMethod("defineClass",
                    String.class, byte[].class, int.class, int.class);
            defineClassMethod.setAccessible(true);
        }
        return defineClassMethod;
    }

    @SuppressWarnings("unchecked")
    protected boolean tryFindTargetClass(ClassLoader loader, String className)
            throws Exception {
        className = className.replaceAll("[.]", "/");
        Method method = getFindClassMethod();
        Object object = method.invoke(loader, targetClassName);
        if (object == null)
            return false;
        targetClass = (Class<? extends T>) object;
        initTargetConstructor();
        return true;
    }

    private Method getFindClassMethod()
            throws NoSuchMethodException {
        if (findClassMethod == null) {
            findClassMethod =
                ClassLoader.class.getDeclaredMethod("findLoadedClass",
                    String.class);
            findClassMethod.setAccessible(true);
        }
        return findClassMethod;
    }

    protected abstract void initTargetConstructor()
            throws Exception;

    protected Type getTypeForPrimitive(Class<?> type) {
        if (type == boolean.class) {
            return Type.BOOLEAN;
        } else if (type == byte.class) {
            return Type.BYTE;
        } else if (type == char.class) {
            return Type.CHAR;
        } else if (type == short.class) {
            return Type.SHORT;
        } else if (type == int.class) {
            return Type.INT;
        } else if (type == long.class) {
            return Type.LONG;
        } else if (type == float.class) {
            return Type.FLOAT;
        } else if (type == double.class) {
            return Type.DOUBLE;
        } else if (type == void.class) {
            return Type.VOID;
        }
        return null;
    }

    protected Type getTypeForClass(Class<?> orig) {
        if (orig.isPrimitive())
            return getTypeForPrimitive(orig);
        int dims = 0;
        while (orig.isArray()) {
            dims++;
            orig = orig.getComponentType();
        }
        if (dims > 0)
            return new ArrayType(getTypeForClass(orig), dims);
        return new ObjectType(orig.getName());
    }

    protected Class<?> getBoxedType(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == char.class) {
            return Character.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        }
        return null;
    }

    /**
     * Extension point: scan a class and return all methods that can be
     * 
     * @return
     */
    protected List<Method> findCandidateMethods() {
        List<Method> l = new ArrayList<Method>();
        for (Method m : originalClass.getMethods()) {
            // if it's implemented, skip it.
            if ((m.getModifiers() & Modifier.ABSTRACT) == 0
                    && !originalClass.isInterface())
                continue;
            l.add(m);
        }
        return l;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public InputStream getClassFile() {
        return new ByteArrayInputStream(classFile);
    }
}
