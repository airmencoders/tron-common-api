package mil.tron.commonapi.service.webdav;

import com.jamesmurty.utils.XMLBuilder;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnStagingIL4OrDevLocal;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemServiceImpl;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Rudimentary implementation of WebDAV translation, for methods that can't easily
 * be answered in the WebDAV controller - most notably the PROPFIND response
 */

@Service
@IfMinioEnabledOnStagingIL4OrDevLocal
public class WebDavServiceImpl implements WebDavService {

    private final DocumentSpaceService documentSpaceService;
    public static final String DT_SERVER_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSS";
    public static final String RESOURCE_TYPE_TAG = "D:resourcetype";

    public WebDavServiceImpl(DocumentSpaceService documentSpaceService) {
        this.documentSpaceService = documentSpaceService;
    }

    /**
     * Formats date time from server into the WebDAV format for the file creation date
     * Example output would be: 1997-12-01T18:27:21-08:00
     * @param fromServer
     * @return
     */
    private String formatCreationDateTimeString(Date fromServer) {
        if (fromServer == null) return "";

        DateFormat formatter = new SimpleDateFormat(DT_SERVER_FORMAT);
        DateFormat davFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX");
        try {
            Date dateTime = formatter.parse(fromServer.toString());
            return davFormat.format(dateTime);
        }
        catch (ParseException ex) {
            return "";
        }
    }

    /**
     * Formats date time from server into the WebDAV format for modified date
     * Example output would be: Mon, 12 Jan 1998 09:25:56 GMT
     * @param fromServer
     * @return
     */
    private String formatModifiedDateTimeString(Date fromServer) {
        if (fromServer == null) return "";
        DateFormat formatter = new SimpleDateFormat(DT_SERVER_FORMAT);
        DateFormat davFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'");
        try {
            Date dateTime = formatter.parse(fromServer.toString());
            return davFormat.format(dateTime);
        }
        catch (ParseException ex) {
            return "";
        }
    }

    /**
     * Performs a WebDAV PROPFIND action (directory listing)
     * @param spaceId document space UUID
     * @param path the path we're PROPFINDing on
     * @param children if true then return the requested directory and all its contents (corresponds to header 'DEPTH = 1')
     *                 otherwise just fetch/include information on the requested element (corresponds to header 'DEPTH = 0')
     * @return Serialized XML PROPFIND tree
     */
    @Override
    public String propFind(UUID spaceId, String path, boolean children) {

        // get the path contents from the service
        FilePathSpecWithContents content = documentSpaceService.getFolderContents(spaceId, path);

        // begin the PROPFIND response with the element requested (the root of the requested path, actual-folder)
        XMLBuilder builder;

        try {
            builder = XMLBuilder.create("D:multistatus").attr("xmlns:D", "DAV:");
        }
        catch (ParserConfigurationException ex) {
            throw new BadRequestException("Could not create XML PROPFIND tree");
        }

        builder = builder.element("D:response")
                    .element("D:href").text(DocumentSpaceFileSystemServiceImpl.joinPathParts("/api/v2/document-space/space/" + spaceId + "/" + content.getFullPathSpec() + "/")).up()
                        .element("D:propstat")
                            .element("D:prop")
                                .element(RESOURCE_TYPE_TAG)
                                    .element("D:collection")
                                .up()
                            .up()
                        .up()
                    .element("D:status").text("HTTP/1.1 200 OK")
                    .up()
                .up()
            .up();

        // if depth header is specified and truthy then we're supposed to include the contents of the whole folder per RFC
        if (children) {
            for (DocumentSpaceFileSystemEntry entry : content.getEntries()) {
                builder = appendStandardProps(builder, entry, spaceId, content);

                if (entry.getSize() == 0) builder = builder.element("D:getcontentlength").up();
                else builder = builder.element("D:getcontentlength").text(String.valueOf(entry.getSize())).up();

                if (entry.isFolder()) builder = builder.element(RESOURCE_TYPE_TAG).element("D:collection").up().up();
                else builder = builder.element(RESOURCE_TYPE_TAG).up();

                builder = builder.up();
                builder = builder.element("D:status").text("HTTP/1.1 200 OK").up().up();
                builder = builder.up();
            }
        }

        try {
            return "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" + builder.asString();
        }
        catch (TransformerException e) {
            throw new BadRequestException("Error serializing PROPFIND XML result");
        }


    }

    /**
     * Creates folder (make collection in webdav parlance)
     * @param spaceId
     * @param path
     * @return
     */
    @Override
    public String mkCol(UUID spaceId, String path) {

        String folderPath = FilenameUtils.getPath(path);
        String name = FilenameUtils.getName(path);
        documentSpaceService.createFolder(spaceId, folderPath, name);

        // if we get here, creation was successful (no throws)
        return "Created";
    }

    private XMLBuilder appendStandardProps(XMLBuilder builder, DocumentSpaceFileSystemEntry entry, UUID spaceId,
                                           FilePathSpecWithContents content) {
        Date lastModified = entry.getLastModifiedOn() != null ? entry.getLastModifiedOn() :
                entry.getCreatedOn();
        return builder
                .element("D:response")
                .element("D:href").text(DocumentSpaceFileSystemServiceImpl.joinPathParts("/api/v2/document-space/space/" +
                        spaceId + "/" + content.getFullPathSpec() + "/" + entry.getItemName()))
                .up()
                .element("D:propstat")
                .element("D:prop")
                .element("D:creationdate")
                .text(formatCreationDateTimeString(entry.getCreatedOn()))
                .up()
                .element("D:getlastmodified")
                .text(formatModifiedDateTimeString(lastModified))
                .up();
    }
}
