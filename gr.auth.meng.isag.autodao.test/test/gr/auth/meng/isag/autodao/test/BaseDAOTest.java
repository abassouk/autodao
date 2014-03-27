package gr.auth.meng.isag.autodao.test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Calendar;
import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Parameter;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;

import org.junit.Before;
import org.mockito.Answers;
import org.mockito.Mock;

public abstract class BaseDAOTest {
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected EntityManager em;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected javax.persistence.Query query;

    @SuppressWarnings("rawtypes")
    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected TypedQuery tq;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    protected EntityTransaction et;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        initMocks(this);
        when(em.createQuery(anyString())).thenReturn(query);
        when(em.createNamedQuery(anyString())).thenReturn(query);
        when(em.createNativeQuery(anyString())).thenReturn(query);

        when(em.createQuery(anyString(), (Class<?>) anyObject()))
                .thenReturn(tq);
        when(em.createNamedQuery(anyString(), (Class<?>) anyObject()))
                .thenReturn(tq);
        when(em.createNativeQuery(anyString(), (Class<?>) anyObject()))
                .thenReturn(tq);

        when(em.getTransaction()).thenReturn(et);

        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setFlushMode(any(FlushModeType.class))).thenReturn(query);
        when(query.setHint(anyString(), anyObject())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.setLockMode(any(LockModeType.class))).thenReturn(query);
        when(query.setParameter(anyInt(), anyObject())).thenReturn(query);
        when(
            query.setParameter(anyInt(), any(Calendar.class),
                any(TemporalType.class))).thenReturn(query);
        when(
            query.setParameter(anyInt(), any(Date.class),
                any(TemporalType.class))).thenReturn(query);
        when(query.setParameter(anyString(), anyObject())).thenReturn(query);
        when(
            query.setParameter(anyString(), any(Calendar.class),
                any(TemporalType.class))).thenReturn(query);
        when(
            query.setParameter(anyString(), any(Date.class),
                any(TemporalType.class))).thenReturn(query);
        when(query.setParameter(any(Parameter.class), anyObject())).thenReturn(
            query);
        when(
            query.setParameter(any(Parameter.class), any(Calendar.class),
                any(TemporalType.class))).thenReturn(query);
        when(
            query.setParameter(any(Parameter.class), any(Date.class),
                any(TemporalType.class))).thenReturn(query);
        
        when(tq.setFirstResult(anyInt())).thenReturn(tq);
        when(tq.setFlushMode(any(FlushModeType.class))).thenReturn(tq);
        when(tq.setHint(anyString(), anyObject())).thenReturn(tq);
        when(tq.setMaxResults(anyInt())).thenReturn(tq);
        when(tq.setLockMode(any(LockModeType.class))).thenReturn(tq);
        when(tq.setParameter(anyInt(), anyObject())).thenReturn(tq);
        when(
            tq.setParameter(anyInt(), any(Calendar.class),
                any(TemporalType.class))).thenReturn(tq);
        when(
            tq.setParameter(anyInt(), any(Date.class),
                any(TemporalType.class))).thenReturn(tq);
        when(tq.setParameter(anyString(), anyObject())).thenReturn(tq);
        when(
            tq.setParameter(anyString(), any(Calendar.class),
                any(TemporalType.class))).thenReturn(tq);
        when(
            tq.setParameter(anyString(), any(Date.class),
                any(TemporalType.class))).thenReturn(tq);
        when(tq.setParameter(any(Parameter.class), anyObject())).thenReturn(
            tq);
        when(
            tq.setParameter(any(Parameter.class), any(Calendar.class),
                any(TemporalType.class))).thenReturn(tq);
        when(
            tq.setParameter(any(Parameter.class), any(Date.class),
                any(TemporalType.class))).thenReturn(tq);
    }
}
