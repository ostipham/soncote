import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
	//client-Server confirmation messages
	private static final String VALID_COMMAND = "command validated";
	private static final String READY = "ready";
	private static final String ABORT = "abort";
	private static final String RECEIVED = "received";
	private static final String DONE = "done";
	
	//commands
	private static final String DOWNLOAD = "download";
	private static final String UPLOAD = "upload";
	
	//credit to Necronet for pattern : https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
	private static final Pattern IP_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private static final int NB_DIGITS_IP_ADDRESS = 15;
	private static final int NB_DIGITS_PORT = 4;
	
	private static BufferedReader in;
    private static PrintWriter out;
    
    private static JTextArea messageArea = new JTextArea(8, 60);
    private JFrame frame = new JFrame("Capitalize Client");
    private JTextField dataField = new JTextField(40);
    
     
    private static Socket socket;
    
    public Client() {

        // Layout GUI
        messageArea.setEditable(false);
        frame.getContentPane().add(dataField, "North");
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");

        // Add Listeners
        dataField.addActionListener(new ActionListener() {
          
            public void actionPerformed(ActionEvent e) {
                
            	//print command
            	messageArea.append("\n");
            	messageArea.append(dataField.getText() + "\n");
            	
            	String confirmation = "";          	
            	String[] command;
            	command = dataField.getText().split(" ");
            	
            	
            	//upload verification is done client side
            	if (command[0].equals(UPLOAD) && (!(command.length == 2) || 
            			!Files.exists(Paths.get("./" + command[1])) || Files.isDirectory(Paths.get("./" + command[1])))) {
            		confirmation = "This file does not exist.";
            	} else {
            		//Send command to server for verification
            		out.println(dataField.getText());
            		try {
            			confirmation = in.readLine();
            		} catch (IOException e3) {
            			e3.printStackTrace();
            		}
            	}
            	
            	if (confirmation.equals(VALID_COMMAND)) {
            		
            		if (command[0].equals(UPLOAD)) {
            			UploadThread upload = new UploadThread(command[1]);
            			upload.start();
            
            		}
            		else if (command[0].equals(DOWNLOAD)) {
            			DownloadThread download = new DownloadThread(command[1]);
            			download.start();		
            		}
            		else {
            			ReadingThread readThread = new ReadingThread();
            			readThread.start(); 
            		}
            		
            	} 
            	else {
            		messageArea.append(confirmation + "\n");
            	}
				
            }
        });
        
    }
    
    private static class ReadingThread extends Thread {
    	
    	public ReadingThread() {}
    	
    	public void run() {
    		int nbLines;
    		try {
    			nbLines = Integer.parseInt(in.readLine());
    			
    			for (int i = 0; i < nbLines; i++) {
    				messageArea.append(in.readLine() + "\n");
    			}
    			
    		} catch (Exception e) {
    			messageArea.append("Error while reading server response.\n");
    			return;
    		}		
    	}
    }
    
    private static class UploadThread extends Thread {
    	private String fileName;
    	
    	public UploadThread(String fileName) {
    		this.fileName = fileName;
    	}
    	
    	public void run() {
    		try {	
    			//wait for confirmation
    			String confirmation = in.readLine(); 
    			if (!confirmation.equals(READY)) {
    				out.println(ABORT);
    				return;
    			}
    				
    			//send file length to server and wait for confirmation
    			File fileForServer = new File("./" + fileName);
    			int length = (int) fileForServer.length();
    			out.println(length);
    			confirmation = in.readLine();
    			if (!confirmation.equals(RECEIVED)) {
    				out.println(ABORT);
    				return;
    			}
    				
    			
    			//prepare file and streams
            	BufferedInputStream inFromFile = new BufferedInputStream(new FileInputStream(fileForServer));
            	byte[] bytesReadFromFile = new byte[length];
            	int sizeReadFromFile;
            	int total = 0;
            	BufferedOutputStream outToSocket = new BufferedOutputStream(socket.getOutputStream());
            	
            	//send to server loop
            	while (total != length) {
            		sizeReadFromFile = inFromFile.read(bytesReadFromFile);
            		outToSocket.write(bytesReadFromFile, 0, sizeReadFromFile);
            		total += sizeReadFromFile;
            	}
            	outToSocket.flush();
            	
            	confirmation = in.readLine();
            	if (!confirmation.equals(DONE)) {
            		messageArea.append("Problem with upload command.\n");
            	}
            	else {
            		messageArea.append("Upload successful.\n");
            	}
    			
            	//close file stream
            	inFromFile.close();
            	
    		} catch (Exception e) {
    			out.println(ABORT);
    			return;
    		}
    	}
    }
    
    private static class DownloadThread extends Thread {
    	private String fileName;
    	
    	public DownloadThread(String fileName) {
    		this.fileName = fileName;
    	}
    	
    	public void run() {
    		try {
        		int substringIndex;
        		int count = 1;
        	    	
        		//if file already in, register second time as fileName(1).pdf, fileName(2).pdf, etc.
        		if (Files.exists(Paths.get("./" + fileName)) && !Files.isDirectory(Paths.get("./" + fileName))) {
        			substringIndex = fileName.indexOf(".", 0);
        			fileName = fileName.substring(0, substringIndex) + "(" + count + ")" + fileName.substring(substringIndex, fileName.length());
        		}
        		while (Files.exists(Paths.get("./" + fileName)) && !Files.isDirectory(Paths.get("./" + fileName))) {
        			fileName = fileName.replace("(" + count + ")", "(" + ++count + ")");
        		}
        	
        		//confirm ready to begin
        		out.println(READY); 
        	
        		//wait for file length and confirm reception
        		String confirmation = in.readLine();
        		if (confirmation.equals(ABORT)) {
        			return;
        		}
        		int length = 0;
        		length = Integer.parseInt(confirmation);	
        		out.println(RECEIVED);
        	
        		//prepare file and streams
        		File newFile = new File("./", fileName);
        		BufferedOutputStream fileWriter = new BufferedOutputStream(new FileOutputStream(newFile));
        		BufferedInputStream inFromSocket = new BufferedInputStream(socket.getInputStream());
        		byte[] bytesFromSocket = new byte[length];
        		int total = 0;
        		int sizeReadFromSocket;
        	
        		//read from socket loop
        		while (total != length) {
        			sizeReadFromSocket = inFromSocket.read(bytesFromSocket);
        			fileWriter.write(bytesFromSocket, 0, sizeReadFromSocket);
        			total += sizeReadFromSocket;
        		}
        		fileWriter.flush();

        		messageArea.append("Download successful.\n");
        		fileWriter.close();  	
        	} catch(Exception e) {
        		messageArea.append("Problem with download.\n");
        		out.println(ABORT);
        		return;
        	}
    	}
    }

	public void connectToServer() throws IOException {
        int port;
        String serverAddress;
        // Java swing objects for information input box
        JPanel panel = new JPanel();
        JTextField ipAddrField = new JTextField(NB_DIGITS_IP_ADDRESS);
        JLabel ipAddrLabel = new JLabel("Insert IP address here (format xxx.xxx.xxx.xxx):");
        JTextField portField = new JTextField(NB_DIGITS_PORT);
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
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // print the initial welcoming messages from the server
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
