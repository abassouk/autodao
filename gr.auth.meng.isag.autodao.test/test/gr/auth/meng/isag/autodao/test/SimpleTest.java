package gr.auth.meng.isag.autodao.test;

import gr.auth.meng.isag.autodao.IPersistenceDAO;
import gr.auth.meng.isag.autodao.internal.DAOAnalysisSimple;

public class SimpleTest extends DAOAnalysisTest{
    @Override
    protected IPersistenceDAO createInstance(DAOAnalysisSimple analysis)
            throws Exception {
        return analysis.createInstance(em);
    }
}
