package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import mil.tron.commonapi.repository.appsource.AppSourcePrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class AppSourceServiceImpl implements AppSourceService {

    private AppSourceRepository appSourceRepository;
    private AppSourcePrivRepository appSourcePrivRepository;

    @Autowired
    public AppSourceServiceImpl(AppSourceRepository appSourceRepository,
                                AppSourcePrivRepository appSourcePrivRepository) {
        this.appSourceRepository = appSourceRepository;
        this.appSourcePrivRepository = appSourcePrivRepository;
    }

    @Override
    public List<AppSourceDto> getAppSources() {
        Iterable<AppSource> appSources = this.appSourceRepository.findAll();
        List<AppSourceDto> appSourceDtos = StreamSupport
                .stream(appSources.spliterator(), false)
                .map(appSource -> AppSourceDto.builder().id(appSource.getId())
                        .name(appSource.getName())
                        .clientCount(appSource.getAppSourcePrivs().size())
                        .build()).collect(Collectors.toList());
        return appSourceDtos;
    }

    public AppSourceDetailsDto createAppSource(AppSourceDetailsDto appSource) {

        AppSource appSourceToSave = AppSource.builder()
                .name(appSource.getName())
                .build();

        Set<AppSourcePriv> appSourcePrivs = appSource.getAppClients()
                .stream().map((privDto) -> AppSourcePriv.builder()
                        .appSource(appSourceToSave)
                        .appClientUser(AppClientUser.builder().id(privDto.getAppClientUser()).build())
                        .privileges(privDto.getPrivileges().stream().collect(Collectors.toSet()))
                .build()).collect(Collectors.toSet());
        AppSource savedAppSource = this.appSourceRepository.saveAndFlush(appSourceToSave);
        Iterable<AppSourcePriv> savedAppSourcePrivs = this.appSourcePrivRepository.saveAll(appSourcePrivs);

        // temporary return actual deserialized obj
        appSource.setId(savedAppSource.getId());
        return appSource;
    }
}
