package davmail.imap;


import java.net.Socket;

import davmail.AbstractServer;

/**
 * Pop3 server
 */
public class ImapServer extends AbstractServer {
    public final static int DEFAULT_PORT = 143;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public ImapServer(int port) {
        super((port == 0) ? ImapServer.DEFAULT_PORT : port);
    }

    public void createConnectionHandler(Socket clientSocket) {
        new ImapConnection(clientSocket);
    }

}
