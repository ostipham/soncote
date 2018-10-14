import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class Client {
	//credit to Necronet for pattern : https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
	private static final Pattern IP_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private BufferedReader in;
    private PrintWriter out;
    private JFrame frame = new JFrame("Capitalize Client");
    private JTextField dataField = new JTextField(40);
    private JTextArea messageArea = new JTextArea(8, 60);

    private Thread readThread;
    /**
     * Constructs the client by laying out the GUI and registering a
     * listener with the textfield so that pressing Enter in the
     * listener sends the textfield contents to the server.
     */
    public Client() {

        // Layout GUI
        messageArea.setEditable(false);
        frame.getContentPane().add(dataField, "North");
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");

        // Add Listeners
        dataField.addActionListener(new ActionListener() {
          
            public void actionPerformed(ActionEvent e) {
                
            	out.println(dataField.getText());
                try {
                	readThread = new ReadingThread(in, messageArea);
                	readThread.start();              	
                } catch (Exception ex) {
                       messageArea.append("Error: " + ex + "\n");
                }
                
            }
        });
        
    }
    
    private static class ReadingThread extends Thread {
    	private BufferedReader in;
    	private JTextArea messageArea;
    	
    	public ReadingThread(BufferedReader in, JTextArea messageArea) {
    		this.in= in;
    		this.messageArea = messageArea;
    	}
    	
    	public void run() {
    		String response;
    		try {
    			response = in.readLine();
    			messageArea.append(response + "\n");
    		} catch (IOException e) {
    			System.out.println("Error in reading thread for client");
    		}
    		
    	}
    	
    }

    /**
     * Implements the connection logic by prompting the end user for
     * the server's IP address, connecting, setting up streams, and
     * consuming the welcome messages from the server.  The Capitalizer
     * protocol says that the server sends three lines of text to the
     * client immediately after establishing a connection.
     */
    @SuppressWarnings("resource")
	public void connectToServer() throws IOException {

        /*
         ajout
         */
        int port;
        String serverAddress;
        Socket socket;
        // Java swing objects for information input box
        JPanel panel = new JPanel();
        JTextField ipAddrField = new JTextField(15);
        JLabel ipAddrLabel = new JLabel("Insert IP address here (format xxx.xxx.xxx.xxx):");
        JTextField portField = new JTextField(4);
        JLabel portLabel = new JLabel("Insert port here (between 5000 and 5050):");
        panel.add(ipAddrLabel);
        panel.add(ipAddrField);
        panel.add(portLabel);
        panel.add(portField);
		
        // Get the server address from a dialog box.
        int ok = JOptionPane.showConfirmDialog(null, panel,"Enter Server informations:", JOptionPane.OK_CANCEL_OPTION);    
        if (ok == JOptionPane.OK_OPTION) {     
        	serverAddress = ipAddrField.getText();
        	try {
        		port = Integer.parseInt(portField.getText());
        	} catch (NumberFormatException e) {
        		port = 0;
        	}
        	
        	// verification loop
        	while (!(port >= 5000 && port <= 5050 && IP_ADDR_PATTERN.matcher(serverAddress).matches())) {
        		ok = JOptionPane.showConfirmDialog(null, panel,"Some informations were faulty. Enter Server informations:", 
        									  	   JOptionPane.OK_CANCEL_OPTION);
        		if (ok == JOptionPane.CANCEL_OPTION)
        			return;
        		serverAddress = ipAddrField.getText();
        		try {
            		port = Integer.parseInt(portField.getText());
            	} catch (NumberFormatException e) {
            		port = 0;
            	}
        	}
        	socket = new Socket(serverAddress, port);
        	System.out.format("The capitalization server is running on %s:%d%n", serverAddress, port);
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Consume the initial welcoming messages from the server
            for (int i = 0; i < 3; i++) {
                messageArea.append(in.readLine() + "\n");
            }
        }
    }

    /**
     * Runs the client application.
     */
    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.pack();
        client.frame.setVisible(true);
        client.connectToServer();
    }
}
