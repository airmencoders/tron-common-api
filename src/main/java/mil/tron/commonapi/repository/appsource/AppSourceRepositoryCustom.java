package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.appsource.AppSource;

public interface AppSourceRepositoryCustom {
    AppSource findByNameIgnoreCaseWithEndpoints(String name);
}
