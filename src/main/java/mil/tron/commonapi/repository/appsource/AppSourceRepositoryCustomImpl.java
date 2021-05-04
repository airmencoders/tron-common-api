package mil.tron.commonapi.repository.appsource;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import mil.tron.commonapi.entity.appsource.AppSource;

public class AppSourceRepositoryCustomImpl implements AppSourceRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AppSource findByNameIgnoreCaseWithEndpoints(String name) {
        return entityManager.createQuery(
            "SELECT a " + 
            "FROM AppSource a " +
            "LEFT JOIN FETCH a.appEndpoints " +
            "WHERE UPPER(a.name) = UPPER(:name) "
        , AppSource.class)
        .setParameter("name", name)
        .getSingleResult();
    }
}
