package gr.auth.meng.isag.autodao.test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import gr.auth.meng.isag.autodao.IPersistenceDAO;
import gr.auth.meng.isag.autodao.annotations.FirstResult;
import gr.auth.meng.isag.autodao.annotations.MaxResults;
import gr.auth.meng.isag.autodao.annotations.NamedQuery;
import gr.auth.meng.isag.autodao.annotations.NativeQuery;
import gr.auth.meng.isag.autodao.annotations.Param;
import gr.auth.meng.isag.autodao.annotations.Position;
import gr.auth.meng.isag.autodao.annotations.Query;
import gr.auth.meng.isag.autodao.internal.DAOAnalysisSimple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.junit.Assert;
import org.junit.Test;

public abstract class DAOAnalysisTest
        extends BaseDAOTest {
    private static Map<Class<?>, DAOAnalysisSimple> amap = new HashMap<>();

    private <T extends IPersistenceDAO> T analyze(Class<T> klass)
            throws Exception {
        DAOAnalysisSimple analysis = amap.get(klass);
        T instance = null;
        if (analysis == null) {
            analysis = new DAOAnalysisSimple(klass);
            analysis.setDebug(true);
            analysis.commence();
            amap.put(klass, analysis);
            instance = klass.cast(createInstance(analysis));
            JavaClass parse =
                new ClassParser(analysis.getClassFile(), instance.getClass()
                        .getCanonicalName()).parse();
            Repository.addClass(parse);
            // new BCELifier(parse, System.out).start();
            // Verifier.main(new String[] { parse.getClassName() });
        } else {
            instance = klass.cast(createInstance(analysis));
        }
        return instance;
    }

    protected abstract IPersistenceDAO createInstance(DAOAnalysisSimple analysis)
            throws Exception;

    // //////////////////////////////////
    interface EmptyDAO
            extends IPersistenceDAO {
    }

    @Test
    public void testBlank()
            throws Exception {
        analyze(EmptyDAO.class);
    }

    // //////////////////////////////////

    interface InvalidSignatureTest
            extends IPersistenceDAO {
        public void thisShouldFail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSignature()
            throws Exception {
        analyze(InvalidSignatureTest.class);
    }

    // //////////////////////////////////
    interface Test3
            extends IPersistenceDAO {
        @Query(value = "Query1")
        public List<Object[]> searchJPA(@Param("one") String foo,
                @Param("two") double f, @Position(3) int bar,
                @Param("four") long d);

        @NativeQuery(value = "Query2")
        public void searchNative(@MaxResults int max, @FirstResult int first);

        @MaxResults(10)
        @NamedQuery(value = "Query3")
        public Test3 searchNamed();
    }

    @Test()
    public void testFullFeatures()
            throws Exception {
        List<Object[]> objs = new ArrayList<Object[]>();

        Test3 t = analyze(Test3.class);
        when(tq.getResultList()).thenReturn(objs);

        List<Object[]> searchJPA = t.searchJPA("str", 1.0d, -1, 3l);

        verify(em).createQuery(eq("Query1"), eq(Object[].class));
        verify(tq).setParameter(eq("one"), eq("str"));
        verify(tq).setParameter(eq("two"), eq(1.0d));
        verify(tq).setParameter(eq(3), eq(-1));
        verify(tq).setParameter(eq("four"), eq(3l));
        verify(tq).getResultList();
        Assert.assertSame(searchJPA, objs);
    }

    @Test()
    public void testNative()
            throws Exception {
        Test3 t = analyze(Test3.class);

        t.searchNative(10, 20);

        verify(em).createNativeQuery("Query2");
        verify(query).setMaxResults(10);
        verify(query).executeUpdate();
    }

    @Test()
    public void testNamed()
            throws Exception {
        Test3 t = analyze(Test3.class);
        when(tq.getSingleResult()).thenReturn(t);

        Test3 res = t.searchNamed();

        verify(em).createNamedQuery(eq("Query3"), eq(Test3.class));
        verify(tq).getSingleResult();
        Assert.assertSame(t, res);
    }

    // //////////////////////////////////

    interface Test4
            extends IPersistenceDAO {
        @MaxResults
        @Query(value = "")
        public void searchNamed();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxResults()
            throws Exception {
        analyze(Test4.class);
    }

    // //////////////////////////////////

    interface Test5
            extends IPersistenceDAO {
        @Query(value = "")
        public void search(String foo);
    }

    @Test(expected = Exception.class)
    public void testMissingParameter()
            throws Exception {
        analyze(Test5.class);
    }

    // //////////////////////////////////

    interface Test6
            extends IPersistenceDAO {
        public void begin();

        public void commit();

        public void rollback();

        public Test6 merge(Object o);

        public Test6 persist(Test6 o);

        public void remove(Object o);

        public void detach(Object o);
    }

    @Test
    public void testDelegateMethods()
            throws Exception {
        analyze(Test6.class);
    }

    // //////////////////////////////////

    interface Test7
            extends IPersistenceDAO {

        public Test7 find(Object key);

        public Test7 find(int key);

        public Test7 findTest(String key);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFindMethods()
            throws Exception {
        Test7 t = analyze(Test7.class);
        when(em.find(any(Class.class), anyObject())).thenReturn(t);

        Test7 b = t.find(new Object());
        verify(em).find(eq(Test7.class), anyObject());
        Assert.assertSame(t, b);

        Test7 a = t.find(10);
        verify(em).find(eq(Test7.class), eq(10));
        Assert.assertSame(t, a);

        Test7 c = t.findTest("key");
        verify(em).find(eq(Test7.class), eq("key"));
        Assert.assertSame(t, c);
    }
}
