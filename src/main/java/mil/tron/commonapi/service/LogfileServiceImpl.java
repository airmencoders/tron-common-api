package mil.tron.commonapi.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import mil.tron.commonapi.dto.LogfileDto;
import mil.tron.commonapi.exception.FileCompressionException;
import mil.tron.commonapi.exception.FileNotExistsException;

@Service
public class LogfileServiceImpl implements LogfileService {
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss");
	private final Path logsPath;
	private final String apiEndpoint;
	
	public LogfileServiceImpl(@Value("${logging.file.path}") String logsDirectory, @Value("${api-prefix.v1}") String apiPrefix) {
		this.logsPath = Paths.get(logsDirectory).toAbsolutePath().normalize();
		this.apiEndpoint = String.format("/%s/logfile/", apiPrefix);
	}

	@Override
	public Iterable<LogfileDto> getAllLogfileInfo() {
		File file = logsPath.toFile();
		List<LogfileDto> dtos = new ArrayList<>();
		
		for (String name : file.list()) {
			dtos.add(convertToDto(name));
		}
		
		return dtos;
	}

	@Override
	public Resource getLogfileResource(String fileName) {
		try {
			Path path = logsPath.resolve(fileName).normalize();
			Resource resource = null;
			
			// If trying to retrieve the most current logfile,
			// compress it before sending
			if (getExtension(fileName).isPresent() && getExtension(fileName).get().equals("log"))
				resource = new ByteArrayResource(gzipFile(path));
			else 
				resource = new UrlResource(path.toUri());

			if (!resource.exists() || !resource.isReadable())
				throw new FileNotExistsException(fileName);
			
			return resource;
		} catch (Exception ex) {
			throw new FileNotExistsException(fileName, ex);
		}
	}
	
	@Override
	public String getLogfileResourceName(Resource resource) {
		String savedFileName = resource.getFilename();

		if (savedFileName == null) {
			String date = dateFormat.format(new Date());
			savedFileName = "spring.log." + date + ".gz";
		}
			
		return savedFileName;
	}

	private LogfileDto convertToDto(String name) {
		LogfileDto dto = new LogfileDto();
		dto.setName(name);
		
		String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(apiEndpoint)
                .path(name)
                .toUriString();
		dto.setDownloadUri(fileDownloadUri);
		
		return dto;
	}
	
	private byte[] gzipFile(Path path) {
		byte[] fileContents;
		
		try {
			fileContents = Files.readAllBytes(path);
		} catch (Exception ex) {
			throw new FileCompressionException(String.format("Could not load file: %s for compression.", path.getFileName()), ex);
		}
		
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		
		try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOutputStream)) {
			gzipOut.write(fileContents);
			gzipOut.finish();
		} catch(Exception ex) {
			throw new FileCompressionException(String.format("Could not compress file: %s.", path.getFileName()), ex);
		}
		
		return byteOutputStream.toByteArray();
	}
	
	private Optional<String> getExtension(String fileName) {
		return Optional.ofNullable(fileName)
			      .filter(f -> f.contains("."))
			      .map(f -> f.substring(fileName.lastIndexOf(".") + 1));
	}
}
