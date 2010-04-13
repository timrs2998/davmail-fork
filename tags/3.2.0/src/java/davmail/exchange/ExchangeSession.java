package davmail.exchange;

import davmail.Settings;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
import org.apache.webdav.lib.Property;
import org.apache.webdav.lib.ResponseEntity;
import org.apache.webdav.lib.methods.CopyMethod;
import org.apache.webdav.lib.methods.MoveMethod;
import org.apache.webdav.lib.methods.PropPatchMethod;
import org.htmlcleaner.CommentToken;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exchange session through Outlook Web Access (DAV)
 */
public class ExchangeSession {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

    public static final SimpleTimeZone GMT_TIMEZONE = new SimpleTimeZone(0, "GMT");

    protected static final int FREE_BUSY_INTERVAL = 15;

    protected static final Vector<String> EVENT_REQUEST_PROPERTIES = new Vector<String>();

    static {
        EVENT_REQUEST_PROPERTIES.add("DAV:getetag");
    }

    protected static final Vector<String> WELL_KNOWN_FOLDERS = new Vector<String>();

    static {
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:inbox");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:deleteditems");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:sentitems");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:sendmsg");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:drafts");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:calendar");
    }

    protected static final Vector<String> DISPLAY_NAME = new Vector<String>();

    static {
        DISPLAY_NAME.add("DAV:displayname");
    }

    protected static final Vector<String> FOLDER_PROPERTIES = new Vector<String>();

    static {
        FOLDER_PROPERTIES.add("DAV:hassubs");
        FOLDER_PROPERTIES.add("DAV:nosubs");
        FOLDER_PROPERTIES.add("urn:schemas:httpmail:unreadcount");
        FOLDER_PROPERTIES.add("http://schemas.microsoft.com/repl/contenttag");
    }

    protected static final Vector<String> CONTENT_TAG = new Vector<String>();

    static {
        CONTENT_TAG.add("http://schemas.microsoft.com/repl/contenttag");
    }


    public static final HashMap<String, String> PRIORITIES = new HashMap<String, String>();

    static {
        PRIORITIES.put("-2", "5 (Lowest)");
        PRIORITIES.put("-1", "4 (Low)");
        PRIORITIES.put("1", "2 (High)");
        PRIORITIES.put("2", "1 (Highest)");
    }

    /**
     * Various standard mail boxes Urls
     */
    private String inboxUrl;
    private String deleteditemsUrl;
    private String sentitemsUrl;
    private String sendmsgUrl;
    private String draftsUrl;
    private String calendarUrl;

    /**
     * Base user mailboxes path (used to select folder)
     */
    private String mailPath;
    private String email;
    private String alias;
    private HttpClient httpClient;

    private final ExchangeSessionFactory.PoolKey poolKey;

    private boolean disableGalLookup = false;

    /**
     * Create an exchange session for the given URL.
     * The session is not actually established until a call to login()
     *
     * @param poolKey session pool key
     * @throws java.io.IOException on error
     */
    ExchangeSession(ExchangeSessionFactory.PoolKey poolKey) throws IOException {
        this.poolKey = poolKey;
        try {
            boolean isBasicAuthentication = isBasicAuthentication(poolKey.url);

            // get proxy configuration from setttings properties
            URL urlObject = new URL(poolKey.url);
            // webdavresource is unable to create the correct url type
            HttpURL httpURL;
            if (poolKey.url.startsWith("http://")) {
                httpURL = new HttpURL(poolKey.userName, poolKey.password,
                        urlObject.getHost(), urlObject.getPort());
            } else if (poolKey.url.startsWith("https://")) {
                httpURL = new HttpsURL(poolKey.userName, poolKey.password,
                        urlObject.getHost(), urlObject.getPort());
            } else {
                throw new IllegalArgumentException("Invalid URL: " + poolKey.url);
            }


            httpClient = DavGatewayHttpClientFacade.getInstance(httpURL);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            HttpMethod method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, poolKey.url);

            if (!isBasicAuthentication) {
                method = formLogin(httpClient, method, poolKey.userName, poolKey.password);
            }
            int status = method.getStatusCode();

            if (status == HttpStatus.SC_UNAUTHORIZED) {
                throw new AuthenticationException("Authentication failed: invalid user or password");
            } else if (status != HttpStatus.SC_OK) {
                throw DavGatewayHttpClientFacade.buildHttpException(method);
            }
            // test form based authentication
            String queryString = method.getQueryString();
            if (queryString != null && queryString.contains("reason=2")) {
                method.releaseConnection();
                if (poolKey.userName != null && poolKey.userName.contains("\\")) {
                    throw new AuthenticationException("Authentication failed: invalid user or password");
                } else {
                    throw new AuthenticationException("Authentication failed: invalid user or password, " +
                            "retry with domain\\user");
                }
            }

            buildMailPath(method);

            // got base http mailbox http url
            getWellKnownFolders();

        } catch (AuthenticationException exc) {
            LOGGER.error(exc.toString());
            throw exc;
        } catch (IOException exc) {
            StringBuffer message = new StringBuffer();
            message.append("DavMail login exception: ");
            if (exc.getMessage() != null) {
                message.append(exc.getMessage());
            } else if (exc instanceof HttpException) {
                message.append(((HttpException) exc).getReasonCode());
                String httpReason = ((HttpException) exc).getReason();
                if (httpReason != null) {
                    message.append(" ");
                    message.append(httpReason);
                }
            } else {
                message.append(exc);
            }

            LOGGER.error(message.toString());
            throw new IOException(message.toString());
        }
        LOGGER.debug("Session " + this + " created");
    }

    protected String formatDate(Date date) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        dateFormatter.setTimeZone(GMT_TIMEZONE);
        return dateFormatter.format(date);
    }

    public boolean isExpired() {
        boolean isExpired = false;
        try {
            DavGatewayHttpClientFacade.executePropFindMethod(
                    httpClient, URIUtil.encodePath(inboxUrl), 0, DISPLAY_NAME);
        } catch (IOException e) {
            isExpired = true;
        }

        return isExpired;
    }

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url exchange base URL
     * @return true if basic authentication detected
     * @throws java.io.IOException unable to connect to exchange
     */
    protected boolean isBasicAuthentication(String url) throws IOException {
        return DavGatewayHttpClientFacade.getHttpStatus(url) == HttpStatus.SC_UNAUTHORIZED;
    }

    protected String getAbsolutePathOrUri(HttpMethod method, String path) throws URIException {
        if (path == null) {
            return method.getURI().getURI();
        } else if (path.startsWith("/")) {
            return path;
        } else {
            String currentPath = method.getPath();
            int end = currentPath.lastIndexOf('/');
            if (end >= 0) {
                return currentPath.substring(0, end + 1) + path;
            } else {
                return path;
            }
        }
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param httpClient httpClient instance
     * @param initmethod form body http method
     * @return logon method
     * @throws java.io.IOException on error
     */
    protected PostMethod buildLogonMethod(HttpClient httpClient, HttpMethod initmethod) throws IOException {

        PostMethod logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(initmethod.getResponseBodyAsStream());
            List forms = node.getElementListByName("form", true);
            if (forms.size() == 1) {
                TagNode form = (TagNode) forms.get(0);
                String logonMethodPath = form.getAttributeByName("action");

                logonMethod = new PostMethod(getAbsolutePathOrUri(initmethod, logonMethodPath));

                List inputList = form.getElementListByName("input", true);
                for (Object input : inputList) {
                    String type = ((TagNode) input).getAttributeByName("type");
                    String name = ((TagNode) input).getAttributeByName("name");
                    String value = ((TagNode) input).getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        logonMethod.addParameter(name, value);
                    }
                }
            } else {
                List frameList = node.getElementListByName("frame", true);
                if (frameList.size() == 1) {
                    String src = ((TagNode) frameList.get(0)).getAttributeByName("src");
                    if (src != null) {
                        LOGGER.debug("Frames detected in form page, try frame content");
                        initmethod.releaseConnection();
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    }
                } else {
                    // another failover for script based logon forms (Exchange 2007)
                    List scriptList = node.getElementListByName("script", true);
                    for (Object script : scriptList) {
                        List contents = ((TagNode) script).getChildren();
                        for (Object content : contents) {
                            if (content instanceof CommentToken) {
                                String scriptValue = ((CommentToken) content).getCommentedContent();
                                int a_sUrlIndex = scriptValue.indexOf("var a_sUrl = \"");
                                int a_sLgnIndex = scriptValue.indexOf("var a_sLgn = \"");
                                if (a_sUrlIndex >= 0 && a_sLgnIndex >= 0) {
                                    a_sUrlIndex += "var a_sUrl = \"".length();
                                    a_sLgnIndex += "var a_sLgn = \"".length();
                                    int a_sUrlEndIndex = scriptValue.indexOf("\"", a_sUrlIndex);
                                    int a_sLgnEndIndex = scriptValue.indexOf("\"", a_sLgnIndex);
                                    if (a_sUrlEndIndex >= 0 && a_sLgnEndIndex >= 0) {
                                        String src = getAbsolutePathOrUri(initmethod,
                                                scriptValue.substring(a_sLgnIndex, a_sLgnEndIndex) +
                                                        scriptValue.substring(a_sUrlIndex, a_sUrlEndIndex));
                                        LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                                    }
                                } else {
                                    a_sLgnIndex = scriptValue.indexOf("var a_sLgnQS = \"");
                                    if (a_sUrlIndex >= 0 && a_sLgnIndex >= 0) {
                                        a_sUrlIndex += "var a_sUrl = \"".length();
                                        a_sLgnIndex += "var a_sLgnQS = \"".length();
                                        int a_sUrlEndIndex = scriptValue.indexOf("\"", a_sUrlIndex);
                                        int a_sLgnEndIndex = scriptValue.indexOf("\"", a_sLgnIndex);
                                        if (a_sUrlEndIndex >= 0 && a_sLgnEndIndex >= 0) {
                                            String src = initmethod.getPath() +
                                                    scriptValue.substring(a_sLgnIndex, a_sLgnEndIndex) +
                                                    scriptValue.substring(a_sUrlIndex, a_sUrlEndIndex);
                                            LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                            HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                            logonMethod = buildLogonMethod(httpClient, newInitMethod);
                                        }
                                    }
                                }
                            }
                        }


                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing login form at " + initmethod.getURI());
        } finally {
            initmethod.releaseConnection();
        }

        if (logonMethod == null) {
            throw new IOException("Authentication form not found at " + initmethod.getURI());
        }
        return logonMethod;
    }

    protected HttpMethod formLogin(HttpClient httpClient, HttpMethod initmethod, String userName, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");

        HttpMethod logonMethod = buildLogonMethod(httpClient, initmethod);
        ((PostMethod) logonMethod).addParameter("username", userName);
        ((PostMethod) logonMethod).addParameter("password", password);
        logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
        return logonMethod;
    }

    protected void buildMailPath(HttpMethod method) throws HttpException {
        // get user mail URL from html body (multi frame)
        BufferedReader mainPageReader = null;
        try {
            mainPageReader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
            String line;
            // find base url
            final String BASE_HREF = "<base href=\"";
            //noinspection StatementWithEmptyBody
            while ((line = mainPageReader.readLine()) != null && line.toLowerCase().indexOf(BASE_HREF) == -1) {
            }
            if (line != null) {
                int start = line.toLowerCase().indexOf(BASE_HREF) + BASE_HREF.length();
                int end = line.indexOf("\"", start);
                String mailBoxBaseHref = line.substring(start, end);
                URL baseURL = new URL(mailBoxBaseHref);
                mailPath = baseURL.getPath();
                LOGGER.debug("Base href found in body, mailPath is " + mailPath);
                buildEmail(method.getPath());
                LOGGER.debug("Current user email is " + email);
            } else {
                // failover for Exchange 2007 : build standard mailbox link with email
                buildEmail(method.getPath());
                mailPath = "/exchange/" + email + "/";
                LOGGER.debug("Current user email is " + email + ", mailPath is " + mailPath);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing main page at " + method.getPath(), e);
        } finally {
            if (mainPageReader != null) {
                try {
                    mainPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing main page at " + method.getPath());
                }
            }
            method.releaseConnection();
        }

        if (mailPath == null) {
            throw new AuthenticationException("Unable to build mail path, authentication failed: password expired ?");
        }
        if (email == null) {
            throw new AuthenticationException("Unable to get email, authentication failed: password expired ?");
        }
    }

    protected void getWellKnownFolders() throws IOException {
        // Retrieve well known URLs
        Enumeration foldersEnum = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(mailPath), 0, WELL_KNOWN_FOLDERS);
        if (!foldersEnum.hasMoreElements()) {
            throw new IOException("Unable to get mail folders");
        }
        ResponseEntity inboxResponse = (ResponseEntity) foldersEnum.
                nextElement();
        Enumeration inboxPropsEnum = inboxResponse.getProperties();
        if (!inboxPropsEnum.hasMoreElements()) {
            throw new IOException("Unable to get mail folders");
        }
        while (inboxPropsEnum.hasMoreElements()) {
            Property inboxProp = (Property) inboxPropsEnum.nextElement();
            if ("inbox".equals(inboxProp.getLocalName())) {
                inboxUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("deleteditems".equals(inboxProp.getLocalName())) {
                deleteditemsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("sentitems".equals(inboxProp.getLocalName())) {
                sentitemsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("sendmsg".equals(inboxProp.getLocalName())) {
                sendmsgUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("drafts".equals(inboxProp.getLocalName())) {
                draftsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("calendar".equals(inboxProp.getLocalName())) {
                calendarUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
        }
        LOGGER.debug("Inbox URL : " + inboxUrl +
                " Trash URL : " + deleteditemsUrl +
                " Sent URL : " + sentitemsUrl +
                " Send URL : " + sendmsgUrl +
                " Drafts URL : " + draftsUrl +
                " Calendar URL : " + calendarUrl
        );
    }

    /**
     * Create message in specified folder.
     * Will overwrite an existing message with same subject in the same folder
     *
     * @param folderUrl   Exchange folder URL
     * @param messageName message name
     * @param properties  message properties (flags)
     * @param messageBody mail body
     * @throws java.io.IOException when unable to create message
     */
    public void createMessage(String folderUrl, String messageName, HashMap<String, String> properties, String messageBody) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(folderUrl + "/" + messageName + ".EML");
        PropPatchMethod patchMethod;
        // create the message first as draft
        if (properties.containsKey("draft")) {
            patchMethod = new PropPatchMethod(messageUrl);
            try {
                // update message with blind carbon copy and other flags
                addProperties(patchMethod, properties);
                int statusCode = httpClient.executeMethod(patchMethod);
                if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                    throw new IOException("Unable to create message " + messageUrl + ": " + statusCode + " " + patchMethod.getStatusLine());
                }

            } finally {
                patchMethod.releaseConnection();
            }
        }

        PutMethod putmethod = new PutMethod(messageUrl);
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        InputStream bodyStream = null;
        try {
            // use same encoding as client socket reader
            bodyStream = new ByteArrayInputStream(messageBody.getBytes());
            putmethod.setRequestBody(bodyStream);
            int code = httpClient.executeMethod(putmethod);

            if (code != HttpStatus.SC_OK && code != HttpStatus.SC_CREATED) {
                throw new IOException("Unable to create message " + messageUrl + ": " + code + " " + putmethod.getStatusLine());
            }
        } finally {
            if (bodyStream != null) {
                try {
                    bodyStream.close();
                } catch (IOException e) {
                    LOGGER.error(e);
                }
            }
            putmethod.releaseConnection();
        }

        // add bcc and other properties
        if (properties.size() > 0) {
            patchMethod = new PropPatchMethod(messageUrl);
            try {
                // update message with blind carbon copy and other flags
                addProperties(patchMethod, properties);
                int statusCode = httpClient.executeMethod(patchMethod);
                if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                    throw new IOException("Unable to patch message " + messageUrl + ": " + statusCode + " " + patchMethod.getStatusLine());
                }

            } finally {
                patchMethod.releaseConnection();
            }
        }
    }

    protected Message buildMessage(ResponseEntity responseEntity) throws URIException {
        Message message = new Message();
        message.messageUrl = URIUtil.decode(responseEntity.getHref());
        Enumeration propertiesEnum = responseEntity.getProperties();
        while (propertiesEnum.hasMoreElements()) {
            Property prop = (Property) propertiesEnum.nextElement();
            String localName = prop.getLocalName();

            if ("x0e080003".equals(localName)) {
                message.size = Integer.parseInt(prop.getPropertyAsString());
            } else if ("uid".equals(localName)) {
                message.uid = prop.getPropertyAsString();
            } else if ("x0e230003".equals(localName)) {
                message.imapUid = Long.parseLong(prop.getPropertyAsString());
            } else if ("read".equals(localName)) {
                message.read = "1".equals(prop.getPropertyAsString());
            } else if ("x10830003".equals(localName)) {
                message.junk = "1".equals(prop.getPropertyAsString());
            } else if ("x10900003".equals(localName)) {
                message.flagged = "2".equals(prop.getPropertyAsString());
            } else if ("x0E070003".equals(localName)) {
                message.draft = "9".equals(prop.getPropertyAsString());
            } else if ("x10810003".equals(localName)) {
                message.answered = "102".equals(prop.getPropertyAsString()) || "103".equals(prop.getPropertyAsString());
                message.forwarded = "104".equals(prop.getPropertyAsString());
            } else if ("date".equals(prop.getLocalName())) {
                message.date = prop.getPropertyAsString();
            } else if ("isdeleted".equals(localName)) {
                message.deleted = "1".equals(prop.getPropertyAsString());
            } else if ("message-id".equals(prop.getLocalName())) {
                message.messageId = prop.getPropertyAsString();
                if (message.messageId.startsWith("<") && message.messageId.endsWith(">")) {
                    message.messageId = message.messageId.substring(1, message.messageId.length() - 1);
                }
            }
        }

        return message;
    }

    protected void addProperties(PropPatchMethod patchMethod, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if ("read".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("read", entry.getValue(), "e", "urn:schemas:httpmail:");
            } else if ("junk".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10830003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("flagged".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10900003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("answered".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10810003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
                if ("102".equals(entry.getValue())) {
                    patchMethod.addPropertyToSet("x10800003", "261", "f", "http://schemas.microsoft.com/mapi/proptag/");
                }
            } else if ("forwarded".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10810003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
                if ("104".equals(entry.getValue())) {
                    patchMethod.addPropertyToSet("x10800003", "262", "f", "http://schemas.microsoft.com/mapi/proptag/");
                }
            } else if ("bcc".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("bcc", entry.getValue(), "b", "urn:schemas:mailheader:");
            } else if ("draft".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x0E070003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("deleted".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("isdeleted", entry.getValue(), "d", "DAV:");
            } else if ("datereceived".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("datereceived", entry.getValue(), "e", "urn:schemas:httpmail:");
            }
        }
    }

    public void updateMessage(Message message, Map<String, String> properties) throws IOException {
        PropPatchMethod patchMethod = new PropPatchMethod(URIUtil.encodePathQuery(message.messageUrl));
        try {
            addProperties(patchMethod, properties);
            int statusCode = httpClient.executeMethod(patchMethod);
            if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                throw new IOException("Unable to update message properties");
            }

        } finally {
            patchMethod.releaseConnection();
        }
    }

    public MessageList getAllMessages(String folderName) throws IOException {
        return searchMessages(folderName, "");
    }

    public MessageList searchMessages(String folderName, String conditions) throws IOException {
        String folderUrl = getFolderPath(folderName);
        MessageList messages = new MessageList();
        String searchRequest = "Select \"DAV:uid\", \"http://schemas.microsoft.com/mapi/proptag/x0e080003\"" +
                "                ,\"http://schemas.microsoft.com/mapi/proptag/x0e230003\"" +
                "                ,\"http://schemas.microsoft.com/mapi/proptag/x10830003\", \"http://schemas.microsoft.com/mapi/proptag/x10900003\"" +
                "                ,\"http://schemas.microsoft.com/mapi/proptag/x0E070003\", \"http://schemas.microsoft.com/mapi/proptag/x10810003\"" +
                "                ,\"urn:schemas:mailheader:message-id\", \"urn:schemas:httpmail:read\", \"DAV:isdeleted\", \"urn:schemas:mailheader:date\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE \"DAV:ishidden\" = False AND \"DAV:isfolder\" = False\n";
        if (conditions != null) {
            searchRequest += conditions;
        }
        searchRequest += "       ORDER BY \"urn:schemas:httpmail:date\" ASC";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(
                httpClient, URIUtil.encodePath(folderUrl), searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();

            Message message = buildMessage(entity);
            messages.add(message);
        }
        Collections.sort(messages);
        return messages;
    }

    public List<Folder> getSubFolders(String folderName, boolean recursive) throws IOException {
        String mode = recursive ? "DEEP" : "SHALLOW";
        List<Folder> folders = new ArrayList<Folder>();
        String searchRequest = "Select \"DAV:nosubs\", \"DAV:hassubs\"," +
                "                \"DAV:hassubs\",\"urn:schemas:httpmail:unreadcount\"" +
                "                FROM Scope('" + mode + " TRAVERSAL OF \"" + getFolderPath(folderName) + "\"')\n" +
                "                WHERE \"DAV:ishidden\" = False AND \"DAV:isfolder\" = True \n" +
                "                      AND (\"DAV:contentclass\"='urn:content-classes:mailfolder' OR \"DAV:contentclass\"='urn:content-classes:folder')";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(
                httpClient, URIUtil.encodePath(mailPath), searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            folders.add(buildFolder(entity));
        }
        return folders;
    }

    protected Folder buildFolder(ResponseEntity entity) throws URIException {
        String href = URIUtil.decode(entity.getHref());
        Folder folder = new Folder();
        Enumeration enumeration = entity.getProperties();
        while (enumeration.hasMoreElements()) {
            Property property = (Property) enumeration.nextElement();
            if ("hassubs".equals(property.getLocalName())) {
                folder.hasChildren = "1".equals(property.getPropertyAsString());
            }
            if ("nosubs".equals(property.getLocalName())) {
                folder.noInferiors = "1".equals(property.getPropertyAsString());
            }
            if ("unreadcount".equals(property.getLocalName())) {
                folder.unreadCount = Integer.parseInt(property.getPropertyAsString());
            }
            if ("contenttag".equals(property.getLocalName())) {
                folder.contenttag = property.getPropertyAsString();
            }
        }
        if (href.endsWith("/")) {
            href = href.substring(0, href.length() - 1);
        }

        // replace well known folder names
        if (href.startsWith(inboxUrl)) {
            folder.folderUrl = href.replaceFirst(inboxUrl, "INBOX");
        } else if (href.startsWith(sentitemsUrl)) {
            folder.folderUrl = href.replaceFirst(sentitemsUrl, "Sent");
        } else if (href.startsWith(draftsUrl)) {
            folder.folderUrl = href.replaceFirst(draftsUrl, "Drafts");
        } else if (href.startsWith(deleteditemsUrl)) {
            folder.folderUrl = href.replaceFirst(deleteditemsUrl, "Trash");
        } else {
            int index = href.indexOf(mailPath.substring(0, mailPath.length() - 1));
            if (index >= 0) {
                if (index + mailPath.length() > href.length()) {
                    folder.folderUrl = "";
                } else {
                    folder.folderUrl = href.substring(index + mailPath.length());
                }
            } else {
                throw new URIException("Invalid folder url: " + folder.folderUrl);
            }
        }
        return folder;
    }

    /**
     * Delete oldest messages in trash.
     * keepDelay is the number of days to keep messages in trash before delete
     *
     * @throws IOException when unable to purge messages
     */
    public void purgeOldestTrashAndSentMessages() throws IOException {
        int keepDelay = Settings.getIntProperty("davmail.keepDelay");
        if (keepDelay != 0) {
            purgeOldestFolderMessages(deleteditemsUrl, keepDelay);
        }
        // this is a new feature, default is : do nothing
        int sentKeepDelay = Settings.getIntProperty("davmail.sentKeepDelay");
        if (sentKeepDelay != 0) {
            purgeOldestFolderMessages(sentitemsUrl, sentKeepDelay);
        }
    }

    public void purgeOldestFolderMessages(String folderUrl, int keepDelay) throws IOException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -keepDelay);
        LOGGER.debug("Delete messages in " + folderUrl + " since " + cal.getTime());

        String searchRequest = "Select \"DAV:uid\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE \"DAV:isfolder\" = False\n" +
                "                   AND \"DAV:getlastmodified\" < '" + formatDate(cal.getTime()) + "'\n";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(
                httpClient, URIUtil.encodePath(folderUrl), searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            String messageUrl = entity.getHref();

            LOGGER.debug("Delete " + messageUrl);
            DavGatewayHttpClientFacade.executeDeleteMethod(httpClient, messageUrl);
        }
    }

    public void sendMessage(List<String> recipients, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        StringBuilder mailBuffer = new StringBuilder();
        StringBuilder recipientBuffer = new StringBuilder();
        boolean inHeader = true;
        boolean inRecipientHeader = false;
        while (!".".equals(line)) {
            mailBuffer.append(line).append((char) 13).append((char) 10);
            line = reader.readLine();

            if (inHeader && line.length() == 0) {
                inHeader = false;
            }

            inRecipientHeader = inRecipientHeader && line.startsWith(" ");

            if ((inHeader && line.length() >= 3) || inRecipientHeader) {
                String prefix = line.substring(0, 3).toLowerCase();
                if ("to:".equals(prefix) || "cc:".equals(prefix) || inRecipientHeader) {
                    inRecipientHeader = true;
                    recipientBuffer.append(line);
                }
            }
            // Exchange 2007 : skip From: header
            if ((inHeader && line.length() >= 5)) {
                String prefix = line.substring(0, 5).toLowerCase();
                if ("from:".equals(prefix)) {
                    line = reader.readLine();
                }
            }
            // patch thunderbird html in reply for correct outlook display
            if (line != null && line.startsWith("<head>")) {
                mailBuffer.append(line).append((char) 13).append((char) 10);
                line = "  <style> blockquote { display: block; margin: 1em 0px; padding-left: 1em; border-left: solid; border-color: blue; border-width: thin;}</style>";
            }
        }
        // remove visible recipients from list
        List<String> visibleRecipients = new ArrayList<String>();
        for (String recipient : recipients) {
            if (recipientBuffer.indexOf(recipient) >= 0) {
                visibleRecipients.add(recipient);
            }
        }
        recipients.removeAll(visibleRecipients);

        StringBuffer bccBuffer = new StringBuffer();
        for (String recipient : recipients) {
            if (bccBuffer.length() > 0) {
                bccBuffer.append(',');
            }
            bccBuffer.append("&lt;");
            bccBuffer.append(recipient);
            bccBuffer.append("&gt;");
        }

        String bcc = bccBuffer.toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        if (bcc.length() > 0) {
            properties.put("bcc", bcc);
        }

        String messageName = UUID.randomUUID().toString();

        createMessage(draftsUrl, messageName, properties, mailBuffer.toString());

        // warning : slide library expects *unencoded* urls
        String tempUrl = draftsUrl + "/" + messageName + ".EML";
        MoveMethod method = new MoveMethod(URIUtil.encodePath(tempUrl), URIUtil.encodePath(sendmsgUrl));
        int status = DavGatewayHttpClientFacade.executeHttpMethod(httpClient, method);
        if (status != HttpStatus.SC_OK) {
            throw DavGatewayHttpClientFacade.buildHttpException(method);
        }
    }

    public String getFolderPath(String folderName) {
        String folderPath;
        if (folderName.startsWith("INBOX")) {
            folderPath = folderName.replaceFirst("INBOX", inboxUrl);
        } else if (folderName.startsWith("Trash")) {
            folderPath = folderName.replaceFirst("Trash", deleteditemsUrl);
        } else if (folderName.startsWith("Drafts")) {
            folderPath = folderName.replaceFirst("Drafts", draftsUrl);
        } else if (folderName.startsWith("Sent")) {
            folderPath = folderName.replaceFirst("Sent", sentitemsUrl);
        } else if (folderName.startsWith("calendar")) {
            folderPath = folderName.replaceFirst("calendar", calendarUrl);
            // absolute folder path
        } else if (folderName.startsWith("/")) {
            folderPath = folderName;
        } else {
            folderPath = mailPath + folderName;
        }
        return folderPath;
    }

    /**
     * Select current folder.
     * Folder name can be logical names INBOX, DRAFTS or TRASH (translated to local names),
     * relative path to user base folder or absolute path.
     *
     * @param folderName folder name
     * @return Folder object
     * @throws IOException when unable to change folder
     */
    public Folder getFolder(String folderName) throws IOException {
        Enumeration folderEnum = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(getFolderPath(folderName)), 0, FOLDER_PROPERTIES);
        Folder folder = null;
        if (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            folder = buildFolder(entity);
            folder.folderName = folderName;
        }
        return folder;
    }

    /**
     * Check folder ctag and reload messages as needed
     *
     * @param currentFolder current folder
     * @return current folder or new refreshed folder
     * @throws IOException on error
     */
    public Folder refreshFolder(Folder currentFolder) throws IOException {
        Folder newFolder = getFolder(currentFolder.folderName);
        if (currentFolder.contenttag == null || !currentFolder.contenttag.equals(newFolder.contenttag)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Contenttag changed on " + currentFolder.folderName + " "
                        + currentFolder.contenttag + " => " + newFolder.contenttag + ", reloading messages");
            }
            newFolder.loadMessages();
            return newFolder;
        } else {
            return currentFolder;
        }
    }

    public void createFolder(String folderName) throws IOException {
        String folderPath = getFolderPath(folderName);
        PropPatchMethod method = new PropPatchMethod(URIUtil.encodePath(folderPath)) {
            public String getName() {
                return "MKCOL";
            }
        };
        method.addPropertyToSet("outlookfolderclass", "IPF.Note", "ex", "http://schemas.microsoft.com/exchange/");
        int status = DavGatewayHttpClientFacade.executeHttpMethod(httpClient, method);
        // ok or alredy exists
        if (status != HttpStatus.SC_MULTI_STATUS && status != HttpStatus.SC_METHOD_NOT_ALLOWED) {
            throw DavGatewayHttpClientFacade.buildHttpException(method);
        }
    }

    public void copyMessage(String messageUrl, String targetName) throws IOException {
        String targetPath = getFolderPath(targetName) + messageUrl.substring(messageUrl.lastIndexOf('/'));
        CopyMethod method = new CopyMethod(URIUtil.encodePath(messageUrl),
                URIUtil.encodePath(targetPath));
        method.setOverwrite(false);
        method.addRequestHeader("Allow-Rename", "t");
        try {
            int statusCode = httpClient.executeMethod(method);
            if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
                throw new HttpException("Unable to move message, target already exists");
            } else if (statusCode != HttpStatus.SC_CREATED) {
                throw DavGatewayHttpClientFacade.buildHttpException(method);
            }
        } finally {
            method.releaseConnection();
        }
    }

    public void moveFolder(String folderName, String targetName) throws IOException {
        String folderPath = getFolderPath(folderName);
        String targetPath = getFolderPath(targetName);
        MoveMethod method = new MoveMethod(URIUtil.encodePath(folderPath),
                URIUtil.encodePath(targetPath));
        method.setOverwrite(false);
        try {
            int statusCode = httpClient.executeMethod(method);
            if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
                throw new HttpException("Unable to move folder, target already exists");
            } else if (statusCode != HttpStatus.SC_CREATED) {
                throw DavGatewayHttpClientFacade.buildHttpException(method);
            }
        } finally {
            method.releaseConnection();
        }
    }

    public void moveToTrash(String encodedPath, String encodedMessageName) throws IOException {
        String source = encodedPath + "/" + encodedMessageName;
        String destination = URIUtil.encodePath(deleteditemsUrl) + "/" + encodedMessageName;
        LOGGER.debug("Deleting : " + source + " to " + destination);
        MoveMethod method = new MoveMethod(source, destination);
        method.addRequestHeader("Overwrite", "f");
        method.addRequestHeader("Allow-rename", "t");

        int status = DavGatewayHttpClientFacade.executeHttpMethod(httpClient, method);
        // do not throw error if already deleted
        if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_NOT_FOUND) {
            throw DavGatewayHttpClientFacade.buildHttpException(method);
        }
        if (method.getResponseHeader("Location") != null) {
            destination = method.getResponseHeader("Location").getValue();
        }

        LOGGER.debug("Deleted to :" + destination);
    }

    public class Folder {
        public String folderUrl;
        public int unreadCount;
        public boolean hasChildren;
        public boolean noInferiors;
        public String folderName;
        public String contenttag;
        public ExchangeSession.MessageList messages;

        public String getFlags() {
            if (noInferiors) {
                return "\\NoInferiors";
            } else if (hasChildren) {
                return "\\HasChildren";
            } else {
                return "\\HasNoChildren";
            }
        }

        public void loadMessages() throws IOException {
            messages = getAllMessages(folderUrl);
        }

        public int size() {
            return messages.size();
        }

        public long getUidNext() {
            return messages.get(messages.size() - 1).getImapUid() + 1;
        }

        public long getImapUid(int index) {
            return messages.get(index).getImapUid();
        }

        public Message get(int index) {
            return messages.get(index);
        }
    }

    public class Message implements Comparable {
        public String messageUrl;
        public String uid;
        public long imapUid;
        public int size;
        public String messageId;
        public String date;
        public boolean read;
        public boolean deleted;
        public boolean junk;
        public boolean flagged;
        public boolean draft;
        public boolean answered;
        public boolean forwarded;

        public long getImapUid() {
            return imapUid;
        }

        public String getImapFlags() {
            StringBuilder buffer = new StringBuilder();
            if (read) {
                buffer.append("\\Seen ");
            }
            if (deleted) {
                buffer.append("\\Deleted ");
            }
            if (flagged) {
                buffer.append("\\Flagged ");
            }
            if (junk) {
                buffer.append("Junk ");
            }
            if (draft) {
                buffer.append("\\Draft ");
            }
            if (answered) {
                buffer.append("\\Answered ");
            }
            if (forwarded) {
                buffer.append("$Forwarded ");
            }
            return buffer.toString().trim();
        }

        public void write(OutputStream os) throws IOException {
            HttpMethod method = null;
            BufferedReader reader = null;
            try {
                method = new GetMethod(URIUtil.encodePath(messageUrl));
                method.setRequestHeader("Content-Type", "text/xml; charset=utf-8");
                method.setRequestHeader("Translate", "f");
                httpClient.executeMethod(method);

                boolean inHTML = false;

                reader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
                OutputStreamWriter isoWriter = new OutputStreamWriter(os);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (".".equals(line)) {
                        line = "..";
                        // patch text/calendar to include utf-8 encoding
                    } else if ("Content-Type: text/calendar;".equals(line)) {
                        StringBuffer headerBuffer = new StringBuffer();
                        headerBuffer.append(line);
                        while ((line = reader.readLine()) != null && line.startsWith("\t")) {
                            headerBuffer.append((char) 13);
                            headerBuffer.append((char) 10);
                            headerBuffer.append(line);
                        }
                        if (headerBuffer.indexOf("charset") < 0) {
                            headerBuffer.append(";charset=utf-8");
                        }
                        headerBuffer.append((char) 13);
                        headerBuffer.append((char) 10);
                        headerBuffer.append(line);
                        line = headerBuffer.toString();
                        // detect html body to patch Exchange html body
                    } else if (line.startsWith("<html")) {
                        inHTML = true;
                    } else if (inHTML && "</html>".equals(line)) {
                        inHTML = false;
                    }
                    if (inHTML) {
                        //    line = line.replaceAll("&#8217;", "'");
                        //    line = line.replaceAll("&#8230;", "...");
                    }
                    isoWriter.write(line);
                    isoWriter.write((char) 13);
                    isoWriter.write((char) 10);
                }
                isoWriter.flush();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        LOGGER.warn("Error closing message input stream", e);
                    }
                }
                if (method != null) {
                    method.releaseConnection();
                }
            }
        }

        public void delete() throws IOException {
            DavGatewayHttpClientFacade.executeDeleteMethod(httpClient, URIUtil.encodePath(messageUrl));
        }

        public void moveToTrash() throws IOException {
            // mark message as read
            HashMap<String, String> properties = new HashMap<String, String>();
            properties.put("read", "1");
            updateMessage(this, properties);

            int index = messageUrl.lastIndexOf('/');
            if (index < 0) {
                throw new IOException("Invalid message url: " + messageUrl);
            }
            String encodedPath = URIUtil.encodePath(messageUrl.substring(0, index));
            String encodedMessageName = URIUtil.encodePath(messageUrl.substring(index + 1));
            ExchangeSession.this.moveToTrash(encodedPath, encodedMessageName);
        }

        public int compareTo(Object message) {
            long compareValue = (getImapUid() - ((Message) message).getImapUid());
            if (compareValue > 0) {
                return 1;
            } else if (compareValue < 0) {
                return -1;
            } else {
                return 0;
            }
        }

        @Override
        public boolean equals(Object message) {
            return message instanceof Message && getImapUid() == ((Message) message).getImapUid();
        }

        @Override
        public int hashCode() {
            return (int)(imapUid ^ (imapUid >>> 32));
        }
    }

    public static class MessageList extends ArrayList<Message> {
        final HashMap<Long, Message> uidMessageMap = new HashMap<Long, Message>();

        @Override
        public boolean add(Message message) {
            uidMessageMap.put(message.getImapUid(), message);
            return super.add(message);
        }

    }

    public class Event {
        protected String href;
        protected String etag;

        protected MimePart getCalendarMimePart(MimeMultipart multiPart) throws IOException, MessagingException {
            MimePart bodyPart = null;
            for (int i = 0; i < multiPart.getCount(); i++) {
                String contentType = multiPart.getBodyPart(i).getContentType();
                if (contentType.startsWith("text/calendar") || contentType.startsWith("application/ics")) {
                    bodyPart = (MimeBodyPart) multiPart.getBodyPart(i);
                    break;
                } else if (contentType.startsWith("multipart")) {
                    Object content = multiPart.getBodyPart(i).getContent();
                    if (content instanceof MimeMultipart) {
                        bodyPart = getCalendarMimePart((MimeMultipart) content);
                    }
                }
            }

            return bodyPart;
        }

        public String getICS() throws IOException {
            String result = null;
            LOGGER.debug("Get event: " + href);
            GetMethod method = new GetMethod(URIUtil.encodePath(href));
            method.setRequestHeader("Content-Type", "text/xml; charset=utf-8");
            method.setRequestHeader("Translate", "f");
            try {
                int status = httpClient.executeMethod(method);
                if (status != HttpStatus.SC_OK) {
                    LOGGER.warn("Unable to get event at " + href + " status: " + status);
                }
                MimeMessage mimeMessage = new MimeMessage(null, method.getResponseBodyAsStream());
                Object mimeBody = mimeMessage.getContent();
                MimePart bodyPart;
                if (mimeBody instanceof MimeMultipart) {
                    bodyPart = getCalendarMimePart((MimeMultipart) mimeBody);
                } else {
                    // no multipart, single body
                    bodyPart = mimeMessage;
                }

                if (bodyPart == null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    mimeMessage.getDataHandler().writeTo(baos);
                    baos.close();
                    throw new IOException("Invalid calendar message content: " + new String(baos.toByteArray(), "UTF-8"));
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bodyPart.getDataHandler().writeTo(baos);
                baos.close();
                result = fixICS(new String(baos.toByteArray(), "UTF-8"), true);

            } catch (MessagingException e) {
                throw new IOException(e.getMessage());
            } finally {
                method.releaseConnection();
            }
            return result;
        }

        public String getPath() {
            int index = href.lastIndexOf('/');
            if (index >= 0) {
                return href.substring(index + 1);
            } else {
                return href;
            }
        }

        public String getEtag() {
            return etag;
        }
    }

    public List<Event> getEventMessages(String principal) throws IOException {
        String folderUrl = replacePrincipal(inboxUrl, principal);
        String searchQuery = "Select \"DAV:getetag\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE \"DAV:contentclass\" = 'urn:content-classes:calendarmessage'\n" +
                "                AND (NOT \"CALDAV:schedule-state\" = 'CALDAV:schedule-processed')\n" +
                "                ORDER BY \"urn:schemas:calendar:dtstart\" DESC\n";
        return getEvents(folderUrl, searchQuery);
    }

    public List<Event> getAllEvents(String principal) throws IOException {
        String folderUrl = replacePrincipal(calendarUrl, principal);
        int caldavPastDelay = Settings.getIntProperty("davmail.caldavPastDelay", Integer.MAX_VALUE);
        String dateCondition = "";
        if (caldavPastDelay != Integer.MAX_VALUE) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -caldavPastDelay);
            dateCondition = "                AND \"urn:schemas:calendar:dtstart\" > '" + formatDate(cal.getTime()) + "'\n";
        }

        String searchQuery = "Select \"DAV:getetag\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE (" +
                "                       \"urn:schemas:calendar:instancetype\" is null OR" +
                "                       \"urn:schemas:calendar:instancetype\" = 1\n" +
                "                OR (\"urn:schemas:calendar:instancetype\" = 0\n" +
                dateCondition +
                "                )) AND \"DAV:contentclass\" = 'urn:content-classes:appointment'\n" +
                "                ORDER BY \"urn:schemas:calendar:dtstart\" DESC\n";
        return getEvents(folderUrl, searchQuery);
    }

    public List<Event> getEvents(String path, String searchQuery) throws IOException {
        List<Event> events = new ArrayList<Event>();
        Enumeration calendarEnum = DavGatewayHttpClientFacade.executeSearchMethod(httpClient, URIUtil.encodePath(path), searchQuery);
        while (calendarEnum.hasMoreElements()) {
            events.add(buildEvent((ResponseEntity) calendarEnum.nextElement()));
        }
        return events;
    }

    public Event getEvent(String principal, String path, String eventName) throws IOException {
        String eventPath = URIUtil.encodePath(replacePrincipal(getFolderPath(path), principal)) + "/" + URIUtil.encodeWithinQuery(eventName);
        LOGGER.debug("getEvent(" + eventPath + "/" + eventName + ")");
        Enumeration calendarEnum = DavGatewayHttpClientFacade.executePropFindMethod(httpClient, eventPath, 0, EVENT_REQUEST_PROPERTIES);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar event");
        }
        return buildEvent((ResponseEntity) calendarEnum.
                nextElement());
    }

    protected Event buildEvent(ResponseEntity calendarResponse) throws URIException {
        Event event = new Event();
        String href = calendarResponse.getHref();
        event.href = URIUtil.decode(href);
        Enumeration propertiesEnumeration = calendarResponse.getProperties();
        while (propertiesEnumeration.hasMoreElements()) {
            Property property = (Property) propertiesEnumeration.nextElement();
            if ("getetag".equals(property.getLocalName())) {
                event.etag = property.getPropertyAsString();
            }
        }
        return event;
    }

    protected String fixICS(String icsBody, boolean fromServer) throws IOException {
        // first pass : detect
        class AllDayState {
            boolean isAllDay = false;
            boolean hasCdoAllDay = false;
            boolean isCdoAllDay = false;
        }
        // Convert event class from and to iCal
        // See https://trac.calendarserver.org/browser/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt
        boolean isAppleiCal = false;
        String eventClass = null;

        List<AllDayState> allDayStates = new ArrayList<AllDayState>();
        AllDayState currentAllDayState = new AllDayState();
        BufferedReader reader = null;
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(':');
                if (index >= 0) {
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    if ("DTSTART;VALUE=DATE".equals(key)) {
                        currentAllDayState.isAllDay = true;
                    } else if ("X-MICROSOFT-CDO-ALLDAYEVENT".equals(key)) {
                        currentAllDayState.hasCdoAllDay = true;
                        currentAllDayState.isCdoAllDay = "TRUE".equals(value);
                    } else if ("END:VEVENT".equals(line)) {
                        allDayStates.add(currentAllDayState);
                        currentAllDayState = new AllDayState();
                    } else if ("PRODID".equals(key) && line.contains("iCal")) {
                        isAppleiCal = true;
                    } else if (isAppleiCal && "X-CALENDARSERVER-ACCESS".equals(key)) {
                        eventClass = value;
                    } else if (!isAppleiCal && "CLASS".equals(key)) {
                        eventClass = value;
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        // second pass : fix
        int count = 0;
        ICSBufferedWriter result = new ICSBufferedWriter();
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;

            while ((line = reader.readLine()) != null) {
                if (!fromServer && currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE";
                } else if (!fromServer && "END:VEVENT".equals(line) && currentAllDayState.isAllDay && !currentAllDayState.hasCdoAllDay) {
                    result.writeLine("X-MICROSOFT-CDO-ALLDAYEVENT:TRUE");
                } else if (!fromServer && !currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE";
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTSTART") && !line.startsWith("DTSTART;VALUE=DATE")) {
                    line = getAllDayLine(line);
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTEND") && !line.startsWith("DTEND;VALUE=DATE")) {
                    line = getAllDayLine(line);
                } else if ("BEGIN:VEVENT".equals(line)) {
                    currentAllDayState = allDayStates.get(count++);
                } else if (line.startsWith("X-CALENDARSERVER-ACCESS:")) {
                    if (!isAppleiCal) {
                        continue;
                    } else {
                        if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                            result.writeLine("CLASS:PRIVATE");
                        } else if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                            result.writeLine("CLASS:CONFIDENTIAL");
                        } else {
                            result.writeLine("CLASS:" + eventClass);
                        }
                    }
                } else if (line.startsWith("EXDATE;TZID=") || line.startsWith("EXDATE:")) {
                    // Apple iCal doesn't support EXDATE with multiple exceptions
                    // on one line.  Split into multiple EXDATE entries (which is
                    // also legal according to the caldav standard).
                    splitExDate(result, line);
                    continue;
                } else if (line.startsWith("X-ENTOURAGE_UUID:")) {
                    // Apple iCal doesn't understand this key, and it's entourage
                    // specific (i.e. not needed by any caldav client): strip it out
                    continue;
                } else if (line.startsWith("CLASS:")) {
                    if (isAppleiCal) {
                        continue;
                    } else {
                        if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:CONFIDENTIAL");
                        } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:PRIVATE");
                        } else {
                            result.writeLine("X-CALENDARSERVER-ACCESS:" + eventClass);
                        }
                    }
                }
                result.writeLine(line);
            }
        } finally {
            reader.close();
        }

        return result.toString();
    }

    protected void splitExDate(ICSBufferedWriter result, String line) {
        int cur = line.lastIndexOf(':') + 1;
        String start = line.substring(0, cur);

        for (int next = line.indexOf(',', cur); next != -1; next = line.indexOf(',', cur)) {
            String val = line.substring(cur, next);
            result.writeLine(start + val);

            cur = next + 1;
        }

        result.writeLine(start + line.substring(cur));
    }

    protected String getAllDayLine(String line) throws IOException {
        int keyIndex = line.indexOf(';');
        int valueIndex = line.lastIndexOf(':');
        int valueEndIndex = line.lastIndexOf('T');
        if (valueIndex < 0 || valueEndIndex < 0) {
            throw new IOException("Invalid ICS line: " + line);
        }
        String dateValue = line.substring(valueIndex + 1, valueEndIndex);
        String key = line.substring(0, Math.max(keyIndex, valueIndex));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date;
        try {
            date = dateFormat.parse(dateValue);
        } catch (ParseException e) {
            throw new IOException("Invalid ICS line: " + line);
        }
        if ("DTEND".equals(key)) {
            date.setTime(date.getTime() - 1);
        }
        return key + ";VALUE=DATE:" + dateValue;
    }


    public static class EventResult {
        public int status;
        public String etag;
    }

    public int sendEvent(String icsBody) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(draftsUrl + "/" + UUID.randomUUID().toString() + ".EML");
        int status = internalCreateOrUpdateEvent(messageUrl, "urn:content-classes:calendarmessage", icsBody, null, null).status;
        if (status != HttpStatus.SC_CREATED) {
            return status;
        } else {
            MoveMethod method = new MoveMethod(URIUtil.encodePath(messageUrl), URIUtil.encodePath(sendmsgUrl));
            status = DavGatewayHttpClientFacade.executeHttpMethod(httpClient, method);
            if (status != HttpStatus.SC_OK) {
                throw DavGatewayHttpClientFacade.buildHttpException(method);
            }
            return status;
        }
    }

    public EventResult createOrUpdateEvent(String principal, String path, String icsBody, String etag, String noneMatch) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(replacePrincipal(calendarUrl, principal) + "/" + URIUtil.decode(path));
        return internalCreateOrUpdateEvent(messageUrl, "urn:content-classes:appointment", icsBody, etag, noneMatch);
    }

    protected String getICSMethod(String icsBody) {
        int methodIndex = icsBody.indexOf("METHOD:");
        if (methodIndex < 0) {
            return "REQUEST";
        }
        int startIndex = methodIndex + "METHOD:".length();
        int endIndex = icsBody.indexOf("\r", startIndex);
        if (endIndex < 0) {
            return "REQUEST";
        }
        return icsBody.substring(startIndex, endIndex);
    }

    static class Participants {
        String attendees;
        String organizer;
    }

    protected Participants getParticipants(String icsBody) throws IOException {
        HashSet<String> attendees = new HashSet<String>();
        String organizer = null;
        BufferedReader reader = null;
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(':');
                if (index >= 0) {
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    int semiColon = key.indexOf(';');
                    if (semiColon >= 0) {
                        key = key.substring(0, semiColon);
                    }
                    if ("ORGANIZER".equals(key) || "ATTENDEE".equals(key)) {
                        int colonIndex = value.indexOf(':');
                        if (colonIndex >= 0) {
                            value = value.substring(colonIndex + 1);
                        }
                        if ("ORGANIZER".equals(key)) {
                            organizer = value;
                            // exclude current user from recipients
                        } else if (!email.equalsIgnoreCase(value)) {
                            attendees.add(value);
                        }
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        Participants participants = new Participants();
        StringBuilder result = new StringBuilder();
        for (String recipient : attendees) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(recipient);
        }
        participants.attendees = result.toString();
        participants.organizer = organizer;
        return participants;
    }

    protected EventResult internalCreateOrUpdateEvent(String messageUrl, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        String uid = UUID.randomUUID().toString();
        int status = 0;
        PutMethod putmethod = new PutMethod(messageUrl);
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Overwrite", "f");
        if (etag != null) {
            putmethod.setRequestHeader("If-Match", etag);
        }
        if (noneMatch != null) {
            putmethod.setRequestHeader("If-None-Match", noneMatch);
        }
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        String method = getICSMethod(icsBody);
        StringBuilder body = new StringBuilder();
        body.append("Content-Transfer-Encoding: 7bit\r\n" +
                "Content-class: ").append(contentClass).append("\r\n");
        // need to parse attendees and organizer to build recipients
        Participants participants = getParticipants(icsBody);
        if ("urn:content-classes:calendarmessage".equals(contentClass)) {
            String recipients;
            if (email.equalsIgnoreCase(participants.organizer)) {
                // current user is organizer => notify all
                recipients = participants.attendees;
            } else {
                // notify only organizer
                recipients = participants.organizer;
            }

            body.append("To: ").append(recipients).append("\r\n");
            // do not send notification if no recipients found
            if (recipients.length() == 0) {
                status = HttpStatus.SC_NO_CONTENT;
            }
        } else {
            // storing appointment, full recipients header
            body.append("To: ").append(participants.attendees).append("\r\n");
            body.append("From: ").append(participants.organizer).append("\r\n");
            // if not organizer, set REPLYTIME to force Outlook in attendee mode 
            if (participants.organizer != null && !email.equalsIgnoreCase(participants.organizer)) {
                if (icsBody.indexOf("METHOD:") < 0) {
                    icsBody = icsBody.replaceAll("BEGIN:VCALENDAR", "BEGIN:VCALENDAR\r\nMETHOD:REQUEST");
                }
                if (icsBody.indexOf("X-MICROSOFT-CDO-REPLYTIME") < 0) {
                    SimpleDateFormat icalFormatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
                    icalFormatter.setTimeZone(GMT_TIMEZONE);

                    icsBody = icsBody.replaceAll("END:VEVENT", "X-MICROSOFT-CDO-REPLYTIME:" +
                            icalFormatter.format(new Date()) + "\r\nEND:VEVENT");
                }
            }
        }
        body.append("MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/alternative;\r\n" +
                "\tboundary=\"----=_NextPart_").append(uid).append("\"\r\n" +
                "\r\n" +
                "This is a multi-part message in MIME format.\r\n" +
                "\r\n" +
                "------=_NextPart_").append(uid).append("\r\n" +
                "Content-class: ").append(contentClass).append("\r\n" +
                "Content-Type: text/calendar;\r\n" +
                "\tmethod=").append(method).append(";\r\n" +
                "\tcharset=\"utf-8\"\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n\r\n");
        body.append(new String(fixICS(icsBody, false).getBytes("UTF-8"), "ISO-8859-1"));
        body.append("------=_NextPart_").append(uid).append("--\r\n");
        putmethod.setRequestBody(body.toString());
        try {
            if (status == 0) {
                status = httpClient.executeMethod(putmethod);
                if (status == HttpURLConnection.HTTP_OK) {
                    if (etag != null) {
                        LOGGER.debug("Updated event " + messageUrl);
                    } else {
                        LOGGER.warn("Overwritten event " + messageUrl);
                    }
                } else if (status != HttpURLConnection.HTTP_CREATED) {
                    LOGGER.warn("Unable to create or update message " + status + " " + putmethod.getStatusLine());
                }
            }
        } finally {
            putmethod.releaseConnection();
        }
        EventResult eventResult = new EventResult();
        eventResult.status = status;
        if (putmethod.getResponseHeader("GetETag") != null) {
            eventResult.etag = putmethod.getResponseHeader("GetETag").getValue();
        }
        return eventResult;
    }


    public void deleteFolder(String path) throws IOException {
        DavGatewayHttpClientFacade.executeDeleteMethod(httpClient, URIUtil.encodePath(getFolderPath(path)));
    }

    public int deleteEvent(String principal, String path, String eventName) throws IOException {
        int status;
        if (path.startsWith("INBOX")) {
            // do not delete calendar messages, mark read and processed
            PropPatchMethod patchMethod = new PropPatchMethod(URIUtil.encodePath(replacePrincipal(getFolderPath(URIUtil.decode(path)), principal)) + "/" + eventName);
            patchMethod.addPropertyToSet("schedule-state", "CALDAV:schedule-processed", "C", "CALDAV:");
            patchMethod.addPropertyToSet("read", "1", "e", "urn:schemas:httpmail:");
            DavGatewayHttpClientFacade.executeMethod(httpClient, patchMethod);
            status = HttpStatus.SC_OK;
        } else {
            status = DavGatewayHttpClientFacade.executeDeleteMethod(httpClient,
                    URIUtil.encodePath(replacePrincipal(getFolderPath(URIUtil.decode(path)), principal)) + "/" + eventName);
        }
        return status;
    }

    public String getInboxCtag() throws IOException {
        return getFolderCtag(inboxUrl);
    }

    public String getCalendarCtag() throws IOException {
        return getFolderCtag(calendarUrl);
    }

    public String getFolderCtag(String folderUrl) throws IOException {
        String ctag = null;
        Enumeration calendarEnum = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(folderUrl), 0, CONTENT_TAG);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get folder object");
        }
        while (calendarEnum.hasMoreElements()) {
            ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                    nextElement();
            Enumeration propertiesEnumeration = calendarResponse.getProperties();
            while (propertiesEnumeration.hasMoreElements()) {
                Property property = (Property) propertiesEnumeration.nextElement();
                if ("http://schemas.microsoft.com/repl/".equals(property.getNamespaceURI())
                        && "contenttag".equals(property.getLocalName())) {
                    ctag = property.getPropertyAsString();
                }
            }
        }
        if (ctag == null) {
            throw new IOException("Unable to get calendar ctag");
        }
        return ctag;
    }

    public String getCalendarEtag() throws IOException {
        String etag = null;
        Enumeration calendarEnum = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(calendarUrl), 0, EVENT_REQUEST_PROPERTIES);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar object");
        }
        while (calendarEnum.hasMoreElements()) {
            ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                    nextElement();
            Enumeration propertiesEnumeration = calendarResponse.getProperties();
            while (propertiesEnumeration.hasMoreElements()) {
                Property property = (Property) propertiesEnumeration.nextElement();
                if ("getetag".equals(property.getLocalName())) {
                    etag = property.getPropertyAsString();
                }
            }
        }
        if (etag == null) {
            throw new IOException("Unable to get calendar etag");
        }
        return etag;
    }

    /**
     * Get current Exchange alias name from login name
     *
     * @return user name
     */
    protected String getAliasFromLogin() {
        // Exchange 2007 : userName is login without domain
        String userName = poolKey.userName;
        int index = userName.indexOf('\\');
        if (index >= 0) {
            userName = userName.substring(index + 1);
        }
        return userName;
    }

    /**
     * Get current Exchange alias name from mailbox name
     *
     * @return user name
     * @throws java.io.IOException on error
     */
    protected String getAliasFromMailPath() throws IOException {
        if (mailPath == null) {
            return null;
        }
        int index = mailPath.lastIndexOf("/", mailPath.length() - 2);
        if (index >= 0 && mailPath.endsWith("/")) {
            return mailPath.substring(index + 1, mailPath.length() - 1);
        } else {
            throw new IOException("Invalid mail path: " + mailPath);
        }
    }

    public String getAliasFromMailboxDisplayName() throws IOException {
        if (mailPath == null) {
            return null;
        }
        String alias = null;
        Enumeration folderEnum = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(mailPath), 0, DISPLAY_NAME);
        if (!folderEnum.hasMoreElements()) {
            throw new IOException("Unable to get mail folder");
        }
        ResponseEntity mailboxResponse = (ResponseEntity) folderEnum.
                nextElement();
        Enumeration mailboxPropsEnum = mailboxResponse.getProperties();
        if (!mailboxPropsEnum.hasMoreElements()) {
            throw new IOException("Unable to get mail folder");
        }
        while (mailboxPropsEnum.hasMoreElements()) {
            Property inboxProp = (Property) mailboxPropsEnum.nextElement();
            if ("displayname".equals(inboxProp.getLocalName())) {
                alias = inboxProp.getPropertyAsString();
            }

        }
        return alias;
    }

    public String replacePrincipal(String folderUrl, String principal) throws IOException {
        if (!alias.equals(principal) && !email.equals(principal)) {
            int index = mailPath.lastIndexOf("/", mailPath.length() - 2);
            if (index >= 0 && mailPath.endsWith("/")) {
                return mailPath.substring(0, index) + "/" + principal + "/" + folderUrl.substring(inboxUrl.lastIndexOf("/"));
            } else {
                throw new IOException("Invalid mail path: " + mailPath);
            }
        } else {
            return folderUrl;
        }
    }

    public String getEmail(String alias) throws IOException {
        String emailResult = null;
        if (alias != null) {
            GetMethod getMethod = new GetMethod("/public/?Cmd=galfind&AN=" + alias);
            try {
                int status = httpClient.executeMethod(getMethod);
                if (status != HttpStatus.SC_OK) {
                    throw new IOException("Unable to get user email from: " + getMethod.getPath());
                }
                Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
                Map<String, String> result = results.get(alias.toLowerCase());
                if (result != null) {
                    emailResult = result.get("EM");
                }

            } finally {
                getMethod.releaseConnection();
            }
        }
        return emailResult;
    }

    public void buildEmail(String methodPath) throws IOException {
        // first try to get email from login name
        alias = getAliasFromLogin();
        email = getEmail(alias);
        // failover: use mailbox name as alias
        if (email == null) {
            alias = getAliasFromMailPath();
            email = getEmail(alias);
        }
        // another failover : get alias from mailPath display name
        if (email == null) {
            alias = getAliasFromMailboxDisplayName();
            email = getEmail(alias);
        }
        if (email == null) {
            // failover : get email from Exchange 2007 Options page
            alias = getAliasFromOptions(methodPath);
            email = getEmail(alias);
        }
        if (email == null) {
            throw new IOException("Unable to get user email with alias " + getAliasFromLogin()
                    + " or " + getAliasFromMailPath()
                    + " or " + getAliasFromOptions(methodPath)
            );
        }
    }

    protected String getAliasFromOptions(String path) {
        String result = null;
        // get user mail URL from html body
        BufferedReader optionsPageReader = null;
        GetMethod optionsMethod = new GetMethod(path + "?ae=Options&t=About");
        try {
            httpClient.executeMethod(optionsMethod);
            optionsPageReader = new BufferedReader(new InputStreamReader(optionsMethod.getResponseBodyAsStream()));
            String line;
            // find mailbox full name
            final String MAILBOX_BASE = "cn=recipients/cn=";
            //noinspection StatementWithEmptyBody
            while ((line = optionsPageReader.readLine()) != null && line.toLowerCase().indexOf(MAILBOX_BASE) == -1) {
            }
            if (line != null) {
                int start = line.toLowerCase().indexOf(MAILBOX_BASE) + MAILBOX_BASE.length();
                int end = line.indexOf("<", start);
                result = line.substring(start, end);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
        } finally {
            if (optionsPageReader != null) {
                try {
                    optionsPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
                }
            }
            optionsMethod.releaseConnection();
        }

        return result;
    }

    /**
     * Get current user email
     *
     * @return user email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Get current user alias
     *
     * @return user email
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Search users in global address book
     *
     * @param searchAttribute exchange search attribute
     * @param searchValue     search value
     * @return List of users
     * @throws java.io.IOException on error
     */
    public Map<String, Map<String, String>> galFind(String searchAttribute, String searchValue) throws IOException {
        Map<String, Map<String, String>> results;
        GetMethod getMethod = new GetMethod(URIUtil.encodePathQuery("/public/?Cmd=galfind&" + searchAttribute + "=" + searchValue));
        try {
            int status = httpClient.executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException(status + "Unable to find users from: " + getMethod.getURI());
            }
            results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
        } finally {
            getMethod.releaseConnection();
        }

        return results;
    }

    public void galLookup(Map<String, String> person) {
        if (!disableGalLookup) {
            GetMethod getMethod = null;
            try {
                getMethod = new GetMethod(URIUtil.encodePathQuery("/public/?Cmd=gallookup&ADDR=" + person.get("EM")));
                int status = httpClient.executeMethod(getMethod);
                if (status != HttpStatus.SC_OK) {
                    throw new IOException(status + "Unable to find users from: " + getMethod.getURI());
                }
                Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "person", "alias");
                // add detailed information
                if (results.size() > 0) {
                    Map<String, String> fullperson = results.get(person.get("AN").toLowerCase());
                    for (Map.Entry<String, String> entry : fullperson.entrySet()) {
                        person.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to gallookup person: " + person + ", disable GalLookup");
                disableGalLookup = true;
            } finally {
                if (getMethod != null) {
                    getMethod.releaseConnection();
                }
            }
        }
    }

    public FreeBusy getFreebusy(String attendee, Map<String, String> valueMap) throws IOException {

        String startDateValue = valueMap.get("DTSTART");
        String endDateValue = valueMap.get("DTEND");
        if (attendee.startsWith("mailto:")) {
            attendee = attendee.substring("mailto:".length());
        }

        SimpleDateFormat icalParser = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        icalParser.setTimeZone(GMT_TIMEZONE);

        SimpleDateFormat shortIcalParser = new SimpleDateFormat("yyyyMMdd");
        shortIcalParser.setTimeZone(GMT_TIMEZONE);

        SimpleDateFormat owaFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        owaFormatter.setTimeZone(GMT_TIMEZONE);

        String url;
        Date startDate;
        Date endDate;
        try {
            if (startDateValue.length() == 8) {
                startDate = shortIcalParser.parse(startDateValue);
            } else {
                startDate = icalParser.parse(startDateValue);
            }
            if (endDateValue.length() == 8) {
                endDate = shortIcalParser.parse(endDateValue);
            } else {
                endDate = icalParser.parse(endDateValue);
            }
            url = "/public/?cmd=freebusy" +
                    "&start=" + owaFormatter.format(startDate) +
                    "&end=" + owaFormatter.format(endDate) +
                    "&interval=" + FREE_BUSY_INTERVAL +
                    "&u=SMTP:" + attendee;
        } catch (ParseException e) {
            throw new IOException(e.getMessage());
        }

        FreeBusy freeBusy = null;
        GetMethod getMethod = new GetMethod(url);
        getMethod.setRequestHeader("Content-Type", "text/xml");

        try {
            int status = httpClient.executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException("Unable to get free-busy from: " + getMethod.getPath());
            }
            String body = getMethod.getResponseBodyAsString();
            int startIndex = body.lastIndexOf("<a:fbdata>");
            int endIndex = body.lastIndexOf("</a:fbdata>");
            if (startIndex >= 0 && endIndex >= 0) {
                String fbdata = body.substring(startIndex + "<a:fbdata>".length(), endIndex);
                freeBusy = new FreeBusy(icalParser, startDate, fbdata);
            }
        } finally {
            getMethod.releaseConnection();
        }

        return freeBusy;
    }

    /**
     * Exchange to iCalendar Free/Busy parser.
     * Free time returns 0, Tentative returns 1, Busy returns 2, and Out of Office (OOF) returns 3
     */
    public static final class FreeBusy {
        final SimpleDateFormat icalParser;
        boolean knownAttendee = true;
        static final HashMap<Character, String> FBTYPES = new HashMap<Character, String>();

        static {
            FBTYPES.put('1', "BUSY-TENTATIVE");
            FBTYPES.put('2', "BUSY");
            FBTYPES.put('3', "BUSY-UNAVAILABLE");
        }

        final HashMap<String, StringBuilder> busyMap = new HashMap<String, StringBuilder>();

        protected StringBuilder getBusyBuffer(char type) {
            String fbType = FBTYPES.get(Character.valueOf(type));
            StringBuilder buffer = busyMap.get(fbType);
            if (buffer == null) {
                buffer = new StringBuilder();
                busyMap.put(fbType, buffer);
            }
            return buffer;
        }

        protected void startBusy(char type, Calendar currentCal) {
            if (type == '4') {
                knownAttendee = false;
            } else if (type != '0') {
                StringBuilder busyBuffer = getBusyBuffer(type);
                if (busyBuffer.length() > 0) {
                    busyBuffer.append(',');
                }
                busyBuffer.append(icalParser.format(currentCal.getTime()));
            }
        }

        protected void endBusy(char type, Calendar currentCal) {
            if (type != '0' && type != '4') {
                getBusyBuffer(type).append('/').append(icalParser.format(currentCal.getTime()));
            }
        }

        public FreeBusy(SimpleDateFormat icalParser, Date startDate, String fbdata) {
            this.icalParser = icalParser;
            if (fbdata.length() > 0) {
                Calendar currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                currentCal.setTime(startDate);

                startBusy(fbdata.charAt(0), currentCal);
                for (int i = 1; i < fbdata.length() && knownAttendee; i++) {
                    currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                    char previousState = fbdata.charAt(i - 1);
                    char currentState = fbdata.charAt(i);
                    if (previousState != currentState) {
                        endBusy(previousState, currentCal);
                        startBusy(currentState, currentCal);
                    }
                }
                currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                endBusy(fbdata.charAt(fbdata.length() - 1), currentCal);
            }
        }

        public void appendTo(StringBuilder buffer) {
            for (Map.Entry<String, StringBuilder> entry : busyMap.entrySet()) {
                buffer.append("FREEBUSY;FBTYPE=").append(entry.getKey())
                        .append(":").append(entry.getValue()).append((char) 13).append((char) 10);
            }
        }
    }

}