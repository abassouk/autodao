package gr.auth.meng.isag.autodao;

import javax.persistence.EntityManager;

/**
 * AutoDAO can create instances that use an {@link IEMProvider} instead of an
 * {@link EntityManager}. This is useful in threaded applications where the same
 * DAO can be accessed from multiple threads.
 * 
 * @author abas
 */
public interface IEMProvider {
    public EntityManager getEntityManager();

    public void releaseEntityManager(EntityManager em);
}
