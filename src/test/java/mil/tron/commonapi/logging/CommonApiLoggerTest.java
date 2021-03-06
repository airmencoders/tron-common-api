package mil.tron.commonapi.logging;


import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.MockToken;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.service.PersonService;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class CommonApiLoggerTest {
    private static final String ENDPOINT = "/v1/person/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();    

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
	private PersonService personService;

    private PrintStream originalSystemOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    public void setUp() throws Exception {
        originalSystemOut = System.out;
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setOut(originalSystemOut);
        System.setOut(originalSystemOut);
    }

    @Test
    public void callBeforeDeleteRequest() throws Throwable {
        mockMvc.perform(delete(ENDPOINT + UUID.randomUUID().toString()).header("authorization", MockToken.token));
        assertEquals(true, outputStreamCaptor.toString().contains("DELETE request"));
    }

    @Test
    public void callBeforePutRequest() throws Throwable {
        PersonDto p = new PersonDto();
        mockMvc.perform(put(ENDPOINT + UUID.randomUUID().toString())
                .header("authorization", MockToken.token)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)));
        assertEquals(true, outputStreamCaptor.toString().contains("PUT request"));
        assertEquals(true, outputStreamCaptor.toString().contains(MockToken.EMAIL_MD5));
    }

    @Test
    public void callBeforePostRequest() throws Throwable {
        PersonDto p = new PersonDto();
        mockMvc.perform(post(ENDPOINT)
                .header("authorization", MockToken.token)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(p)));
        assertEquals(true, outputStreamCaptor.toString().contains("POST request"));
    }

    @Test
    public void callBeforeGetRequest() throws Throwable {
        mockMvc.perform(get(ENDPOINT)
                .header("authorization", MockToken.token)
                .accept(MediaType.APPLICATION_JSON));
        assertEquals(true, outputStreamCaptor.toString().contains("GET request"));
    }

    @Test
    public void callExceptionHappened() throws Throwable {
        HttpServletRequest curRequest =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getRequest();
        CommonApiLogger logger = new CommonApiLogger(curRequest);
        JoinPoint jp = new JoinPoint() {
            @Override
            public String toShortString() {
                return null;
            }

            @Override
            public String toLongString() {
                return "test";
            }

            @Override
            public Object getThis() {
                return null;
            }

            @Override
            public Object getTarget() {
                return null;
            }

            @Override
            public Object[] getArgs() {
                return new Object[0];
            }

            @Override
            public Signature getSignature() {
                return new Signature() {
                    @Override
                    public String toShortString() {
                        return null;
                    }

                    @Override
                    public String toLongString() {
                        return "test";
                    }

                    @Override
                    public String getName() {
                        return null;
                    }

                    @Override
                    public int getModifiers() {
                        return 0;
                    }

                    @Override
                    public Class getDeclaringType() {
                        return null;
                    }

                    @Override
                    public String getDeclaringTypeName() {
                        return null;
                    }
                };

            }

            @Override
            public SourceLocation getSourceLocation() {
                return null;
            }

            @Override
            public String getKind() {
                return null;
            }

            @Override
            public StaticPart getStaticPart() {
                return null;
            }

        };
        logger.exceptionThrown(jp, new Exception());
        assertEquals(true, outputStreamCaptor.toString().contains("Exception thrown"));

    }
}
