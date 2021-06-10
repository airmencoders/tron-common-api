package mil.tron.commonapi.service.fieldauth;

import mil.tron.commonapi.annotation.efa.ProtectedField;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequestScope
public class EntityFieldRunner implements ApplicationRunner {

    @Autowired
    PrivilegeRepository privilegeRepository;

    private static final String PERSON_PREFIX = "Person-";
    private static final String ORG_PREFIX = "Organization-";
    private final List<Field> personFields = FieldUtils.getFieldsListWithAnnotation(Person.class, ProtectedField.class);
    private final List<Field> orgFields = FieldUtils.getFieldsListWithAnnotation(Organization.class, ProtectedField.class);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        buildEntityPrivileges();
    }

    public void buildEntityPrivileges() {

        for (Field f : personFields) {
            String privName = PERSON_PREFIX + f.getName();
            Optional<Privilege> p = privilegeRepository.findByName(privName);
            if (p.isEmpty()) {
                privilegeRepository.save(Privilege.builder()
                        .name(PERSON_PREFIX + f.getName())
                        .build());
            }
        }

//        List<Privilege> personPrivs = privilegeRepository.findAll()
//                .stream()
//                .filter(item -> item.getName().startsWith(PERSON_PREFIX))
//                .collect(Collectors.toList());
//
//        for (Privilege priv : personPrivs) {
//            if (!personFields
//                    .stream()
//                    .map(Field::getName)
//                    .collect(Collectors.toList())
//                    .contains(priv.getName().replaceFirst(PERSON_PREFIX, ""))) {
//
//                purgePrivilege(priv);
//            }
//        }

        for (Field f : orgFields) {
            String privName = ORG_PREFIX + f.getName();
            Optional<Privilege> p = privilegeRepository.findByName(privName);
            if (p.isEmpty()) {
                privilegeRepository.save(Privilege.builder()
                        .name(ORG_PREFIX + f.getName())
                        .build());
            }
        }

//        List<Privilege> orgPrivs = privilegeRepository.findAll()
//                .stream()
//                .filter(item -> item.getName().startsWith(ORG_PREFIX))
//                .collect(Collectors.toList());
//
//        for (Privilege priv : orgPrivs) {
//            if (!orgFields
//                    .stream()
//                    .map(Field::getName)
//                    .collect(Collectors.toList())
//                    .contains(priv.getName().replaceFirst(ORG_PREFIX, ""))) {
//
//                purgePrivilege(priv);
//            }
//        }
    }

    private void purgePrivilege(Privilege privilege) {
        privilegeRepository.deletePrivilegeFromAppClients(privilege.getId());
        privilegeRepository.deletePrivilegeFromDashboardUsers(privilege.getId());
        privilegeRepository.deletePrivilegeById(privilege.getId());
    }
}
