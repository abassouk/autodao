package gr.auth.meng.isag.autodao.test;

import java.util.List;

import gr.auth.meng.isag.autodao.IEMProvider;
import gr.auth.meng.isag.autodao.IPersistenceDAO;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.apache.bcel.util.BCELifier;
import org.junit.Test;

public class DumpTest {
    public static class FooTest
            implements IPersistenceDAO {
        protected IEMProvider _emprov;

        public List<FooTest> doSomething(String query) {
            EntityManager em = _emprov.getEntityManager();
            try {
                try {
                    return em.createNamedQuery(query, FooTest.class)
                            .getResultList();
                } catch (NoResultException e) {
                    return null;
                }
            } finally {
                _emprov.releaseEntityManager(em);
            }
        }
    }

    @Test
    public void testDump()
            throws Exception {
        BCELifier.main(new String[] { FooTest.class.getName() });
    }
}
