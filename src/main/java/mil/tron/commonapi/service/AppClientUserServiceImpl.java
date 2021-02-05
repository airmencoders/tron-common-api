package mil.tron.commonapi.service;

import org.springframework.stereotype.Service;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;

@Service
public class AppClientUserServiceImpl implements AppClientUserService {
	AppClientUserRespository userRepository;
	
	public AppClientUserServiceImpl(AppClientUserRespository userRepository) {
		this.userRepository = userRepository;
	}
	
	@Override
	public Iterable<AppClientUser> getAppClientUsers() {
		return userRepository.findAll();
	}

}
