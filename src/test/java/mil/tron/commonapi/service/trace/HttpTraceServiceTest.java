package mil.tron.commonapi.service.trace;

import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.HttpLogsRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class HttpTraceServiceTest {

    @Mock
    HttpLogsRepository httpLogsRepository;

    @InjectMocks
    HttpTraceService service;

    @Test
    void testGetLogsFromDate() {
        Mockito.when(httpLogsRepository.findByRequestTimestampGreaterThanEqual(Mockito.any(), Mockito.any()))
                .thenReturn(new SliceImpl<>(Lists.newArrayList(HttpLogEntry.builder()
                        .remoteIp("blah")
                        .build(),
                        HttpLogEntry.builder()
                        .remoteIp("blah2")
                        .build())));

        assertEquals(2, service.getLogsFromDate(new Date(), null).size());
    }


    @Test
    void testGetAllStubEmpty() {
        assertEquals(0, service.findAll().size());
    }

    @Test
    void testGetSingleRecord() {
        Mockito.when(httpLogsRepository.findById(Mockito.any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new HttpLogEntry()));

        assertThrows(RecordNotFoundException.class, () -> service.getLogInfoDetails(UUID.randomUUID()));
        assertDoesNotThrow(() -> service.getLogInfoDetails(UUID.randomUUID()));
    }

}
