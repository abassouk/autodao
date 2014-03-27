package gr.auth.meng.isag.autodao.internal;

import gr.auth.meng.isag.autodao.IPersistenceDAO;

import javax.persistence.EntityManager;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

/** 
 * @author abas
 */
public class DAOAnalysisSimple
        extends DAOAnalysis
        implements Constants {
    public DAOAnalysisSimple(Class<? extends IPersistenceDAO> itemClass) {
        super(itemClass);
    }

    public IPersistenceDAO createInstance(EntityManager em)
            throws Exception {
        return defConstructor.newInstance(em);
    }

    @Override
    protected void initTargetConstructor()
            throws Exception {
        defConstructor = targetClass.getConstructor(EntityManager.class);
    }

    @Override
    protected void setUpClassfile() {
        ObjectType type = new ObjectType(EM_TYPE);
        FieldGen fieldGen = new FieldGen(ACC_PRIVATE, type, "em", _cp);
        _cg.addField(fieldGen.getField());

        createInheritedConstructor(EntityManager.class);
        createGetEntityManager();
    }

    protected void createGetEntityManager() {
        GeneratedMethod gm =
            new GeneratedMethod(ACC_PUBLIC, new ObjectType(EM_TYPE),
                    "getEntityManager", Type.NO_ARGS);
        InstructionList il = gm.start();

        il.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        il.append(_factory.createFieldAccess(targetClassName, "em",
            new ObjectType(EM_TYPE), Constants.GETFIELD));
        il.append(InstructionFactory.createReturn(Type.OBJECT));

        gm.done();
    }

    @Override
    protected void writeMethodPreamble(GeneratedMethod method,
            InstructionList il) {
        il.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        il.append(_factory.createInvoke(targetClassName, "getEntityManager",
            new ObjectType(EM_TYPE), Type.NO_ARGS, Constants.INVOKEVIRTUAL));
    }

    @Override
    protected void writeMethodEpilogue(GeneratedMethod method,
            InstructionList il) {
    }

    @Override
    protected String getNameSuffix() {
        return "$$impl1";
    }
}
