package mil.tron.commonapi.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import mil.tron.commonapi.dto.LogfileDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.FileCompressionException;
import mil.tron.commonapi.exception.FileNotExistsException;

@Service
public class LogfileServiceImpl implements LogfileService {
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss");
	private final Path logsPath;
	private final String apiEndpoint;
	
	public LogfileServiceImpl(@Value("${logging.file.path}") String logsDirectory, @Value("${api-prefix.v2}") String apiPrefix) {
		this.logsPath = Paths.get(logsDirectory).toAbsolutePath().normalize();
		this.apiEndpoint = String.format("/%s/logfile/", apiPrefix);
	}

	@Override
	public Iterable<LogfileDto> getAllLogfileInfo() {
		List<LogfileDto> dtos = new ArrayList<>();
		
		for (String name : getNamesFromPath()) {
			dtos.add(convertToDto(name));
		}
		
		return dtos;
	}

	@Override
	public Resource getLogfileResource(String fileName) {
		Path path;
		
		// Retrieve only the filename to further help
		// prevent path traversal
		fileName = FilenameUtils.getName(fileName);
		try {
			path = logsPath.resolve(fileName).normalize();
		} catch (InvalidPathException ex) {
			throw new BadRequestException("Could not resolve filename: " + fileName);
		}
		
		Resource resource = null;
		
		Optional<String> fileType = getExtension(fileName);
		
		// If trying to retrieve the most current logfile,
		// compress it before sending
		if (fileType.isPresent() && fileType.get().equals("log"))
			resource = new ByteArrayResource(gzipFileAsBytes(path));
		else
			resource = createUrlResourceFromPathString(path.toUri().toString());

		if (!resource.exists() || !resource.isReadable())
			throw new FileNotExistsException(fileName);
		
		return resource;
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
	
	protected String[] getNamesFromPath() {
		File file = logsPath.toFile();
		
		return file.list();
	}
	
	protected UrlResource createUrlResourceFromPathString(String url) {
		try {
			return new UrlResource(url);
		} catch (MalformedURLException e) {
			throw new BadRequestException(e.getLocalizedMessage());
		}
	}

 	protected LogfileDto convertToDto(String name) {
		LogfileDto dto = new LogfileDto();
		
		dto.setName(name);
		dto.setDownloadUri(createDownloadUri(name));
		
		return dto;
	}
 	
 	protected String createDownloadUri(String name) {
 		return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(apiEndpoint)
                .path(name)
                .toUriString();
 	}
	
	protected byte[] gzipFileAsBytes(Path path) {
		byte[] fileContents = readPathFileAsBytes(path);
		
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		
		try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOutputStream)) {
			gzipOut.write(fileContents);
			gzipOut.finish();
		} catch(Exception ex) {
			throw new FileCompressionException(String.format("Could not compress file: %s.", path.getFileName()), ex);
		}
		
		return byteOutputStream.toByteArray();
	}
	
	protected byte[] readPathFileAsBytes(Path path) {
		try {
			return Files.readAllBytes(path);
		} catch (Exception ex) {
			throw new FileCompressionException(String.format("Could not load file: %s for compression.", path.getFileName()), ex);
		}
	}
	
	protected Optional<String> getExtension(String fileName) {
		return Optional.ofNullable(fileName)
			      .filter(f -> f.contains("."))
			      .map(f -> f.substring(fileName.lastIndexOf(".") + 1));
	}
}
