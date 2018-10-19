import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
	private static BufferedReader in;
    private static PrintWriter out;
    private JFrame frame = new JFrame("Capitalize Client");
    private JTextField dataField = new JTextField(40);
    private JTextArea messageArea = new JTextArea(8, 60);
    
    private static String ipAddr;

    private Thread readThread;
    
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
    	private boolean isThreadRunning;
    	
    	public ReadingThread(BufferedReader in, JTextArea messageArea) {
    		this.in= in;
    		this.messageArea = messageArea;
    		this.isThreadRunning = true;
    	}
    	
    	public void run() {
    		String response;
    		String[] command;
    		try {
    			
    				response = in.readLine();			
    				messageArea.append(response + "\n");
    				
    				if (response != null) {
    					command = response.split(" ");
    					if (command[0].equals("upload")) {
    						UploadThread upload = new UploadThread(command[1], messageArea);
    						upload.start();
    					}
    					if (command[0].equals("download")) {
    						DownloadThread download = new DownloadThread(command[1]);
    						download.start();
    					}
    				}
    				
    				
    		} catch (IOException e) {
    			System.out.println("Error in reading thread for client");
    		}		
    	}
    }
    
    private static class UploadThread extends Thread {
    	private String fileName;
    	JTextArea messageArea;
    	
    	public UploadThread(String fileName, JTextArea messageArea) {
    		this.fileName = fileName;
    		this.messageArea = messageArea;
    	}
    	
    	public void run() {
    		try {
    			/*String confirmation = "";
    			while (!confirmation.equals("ready"))
    				confirmation = in.readLine();
    			messageArea.append("confirmed.\n");*/
    			
    			Socket outputSocket = new Socket(ipAddr, 5060);
    			OutputStream output = outputSocket.getOutputStream();
    			
    			
            	File fileForServer = new File("./" + fileName);
            	InputStream inFromFile = new FileInputStream(fileForServer);
            	byte[] bytesReadFromFile = new byte[8192];
            	int sizeReadFromFile;
            	while ((sizeReadFromFile = inFromFile.read(bytesReadFromFile)) > 0) {
            		output.write(bytesReadFromFile, 0, sizeReadFromFile);
            	}
            	
            	
    			/*while (!confirmation.equals("fini")) {
    				confirmation = in.readLine();
    			}
    			out.println("fini");*/
            	
            	inFromFile.close();
            	outputSocket.close();
    		} catch (Exception e) {
    			System.out.println("Error uploading file. Error = " + e.toString() + "\n");
    		}
    	}
    }
    
    private static class DownloadThread extends Thread {
    	private InputStream input;
    	private String fileName;
    	
    	public DownloadThread(String fileName) {
    		this.input = input;
    		this.fileName = fileName;
    	}
    	
    	public void run() {
    		try {
            	int substringIndex;
            	int count = 1;
            	    	
            	//if file already in, register second time as fileName(1).pdf, fileName(2).pdf, etc.
            	while (Files.exists(Paths.get("./" + fileName)) && !Files.isDirectory(Paths.get("./" + fileName))) {
            		substringIndex = fileName.indexOf(".", 0);
            		fileName = fileName.substring(0, substringIndex) + "(" + count++ + ")" + fileName.substring(substringIndex, fileName.length()-1);
            	}
            	
            	File newFile = new File("./", fileName); //Adding directory will write file in it
            	OutputStream fileWriter = new FileOutputStream(newFile);
            	byte[] bytesFromSocket = new byte[8192];
            	int sizeReadFromSocket;
            	
            	while ((sizeReadFromSocket = input.read(bytesFromSocket)) > 0) {
            		fileWriter.write(bytesFromSocket, 0, sizeReadFromSocket);
            	}
            	fileWriter.flush();
            	fileWriter.close();
            	//Do not close the socket input stream. It is still used by BufferedReader in.
    		} catch (Exception e) {
    			System.out.println("Error downloading file. Error = " + e.toString() + "\n");
    		}
    	}
    }

	public void connectToServer() throws IOException {
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
        	ipAddr = ipAddrField.getText();
        	try {
        		port = Integer.parseInt(portField.getText());
        	} catch (NumberFormatException e) {
        		port = 0;
        	}
        	
        	// verification loop
        	while (!(port >= 5000 && port <= 5050 && IP_ADDR_PATTERN.matcher(ipAddr).matches())) {
        		ok = JOptionPane.showConfirmDialog(null, panel,"Some informations were faulty. Enter Server informations:", 
        									  	   JOptionPane.OK_CANCEL_OPTION);
        		if (ok == JOptionPane.CANCEL_OPTION)
        			return;
        		ipAddr = ipAddrField.getText();
        		try {
            		port = Integer.parseInt(portField.getText());
            	} catch (NumberFormatException e) {
            		port = 0;
            	}
        	}
        	socket = new Socket(ipAddr, port);
        	System.out.format("The capitalization server is running on %s:%d%n", ipAddr, port);
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
