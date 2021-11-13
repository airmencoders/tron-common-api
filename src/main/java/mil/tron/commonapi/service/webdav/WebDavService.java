package mil.tron.commonapi.service.webdav;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.util.UUID;

public interface WebDavService {

    String propFind(UUID spaceId, String path, boolean children) throws ParserConfigurationException, TransformerException;
    String mkCol(UUID spaceId, String path);
}
