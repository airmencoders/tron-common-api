package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.Person;

public interface PersonUniqueChecksService {
    boolean personEmailIsUnique(Person person);
    boolean personDodidIsUnique(Person person);
}
