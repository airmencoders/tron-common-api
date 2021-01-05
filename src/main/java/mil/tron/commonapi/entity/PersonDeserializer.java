package mil.tron.commonapi.entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.service.PersonServiceImpl;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class PersonDeserializer extends StdDeserializer<Person> {

    private PersonRepository repo = PersonServiceImpl.repository;

    public PersonDeserializer() {
        this(null);
    }
    public PersonDeserializer(Class<?> cls) {
        super(cls);
    }

    @Override
    public Person deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode node = codec.readTree(jsonParser);

        String uuid = node.asText();
        return repo.findById(UUID.fromString(uuid)).get();
    }
}
