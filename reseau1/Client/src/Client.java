import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
	private static final String RESPONSE_END = "done";
	private static final String BACK = "..";
	private static final String INVALID_COMMAND = "invalid";
	private static final String CD = "cd";
	private static final String LS = "ls";
	private static final String MKDIR = "mkdir";
	private static final String DOWNLOAD = "download";
	private static final String UPLOAD = "upload";
	private static final String EXIT = "exit";
	
	//credit to Necronet for pattern : https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
	private static final Pattern IP_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private static final int NB_DIGITS_IP_ADDRESS = 15;
	private static final int NB_DIGITS_PORT = 4;
	
	private static BufferedReader in;
    private static PrintWriter out;
    
    private JFrame frame = new JFrame("Capitalize Client");
    private JTextField dataField = new JTextField(40);
    private JTextArea messageArea = new JTextArea(8, 60);
    
    private static String ipAddr; 
    private static Socket socket;
    
    public Client() {

        // Layout GUI
        messageArea.setEditable(false);
        frame.getContentPane().add(dataField, "North");
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");

        // Add Listeners
        dataField.addActionListener(new ActionListener() {
          
            public void actionPerformed(ActionEvent e) {
                
            	messageArea.append(dataField.getText() + "\n");
            	out.println(dataField.getText());
            	
            	String[] command;
            	command = dataField.getText().split(" ");
				if (command[0].equals(UPLOAD)) {
					try {
						UploadThread upload = new UploadThread(command[1], messageArea, socket.getOutputStream());
						upload.start();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				else if (command[0].equals(DOWNLOAD)) {
					try {
						DownloadThread download = new DownloadThread(command[1], messageArea, socket.getInputStream());
						download.start();
					} catch (IOException e2) {
						e2.printStackTrace();
					}
					
				}
				else {
					ReadingThread readThread = new ReadingThread(in, messageArea);
					readThread.start(); 
				}
            }
        });
        
    }
    
    private static class ReadingThread extends Thread {
    	private BufferedReader in;
    	private JTextArea messageArea;
    	private boolean isReading;
    	
    	public ReadingThread(BufferedReader in, JTextArea messageArea) {
    		this.in= in;
    		this.messageArea = messageArea;
    		this.isReading = true;
    	}
    	
    	public void run() {
    		String response;
    		try {
    			messageArea.append("start reading\n");
    			while (isReading) {
    				
    				response = in.readLine();
    				if (response != null && response.equals(RESPONSE_END)) {
    					messageArea.append("\n");
    					isReading = false;
    				}
    				else {
    					messageArea.append(response + "\n");
    				}
    			}
    			messageArea.append("stop reading\n");
    		} catch (IOException e) {
    			System.out.println("Error in reading thread for client");
    		}		
    	}
    }
    
    private static class UploadThread extends Thread {
    	private String fileName;
    	JTextArea messageArea;
    	OutputStream outToSocket;
    	
    	public UploadThread(String fileName, JTextArea messageArea, OutputStream outToSocket) {
    		this.fileName = fileName;
    		this.messageArea = messageArea;
    		this.outToSocket = outToSocket;
    	}
    	
    	public void run() {
    		try {	
    			String confirmation = in.readLine();
    			if (!confirmation.equals("begin"))
    				return;

    			File fileForServer = new File("./" + fileName);
    			int length = (int) fileForServer.length();
    			out.println(length);
    			confirmation = in.readLine();
    			if (!confirmation.equals("received"))
    				return;
    			
            	FileInputStream fileInput = new FileInputStream(fileForServer);
            	BufferedInputStream inFromFile = new BufferedInputStream(fileInput);
            	byte[] bytesReadFromFile = new byte[100];
            	int sizeReadFromFile;
            	int total = 0;
            	BufferedOutputStream outt = new BufferedOutputStream(outToSocket);
            	
            	while (total != length) {
            		sizeReadFromFile = inFromFile.read(bytesReadFromFile);
            		outt.write(bytesReadFromFile, 0, sizeReadFromFile);
            		total += sizeReadFromFile;
            	}
            	outt.flush();
            	
            	confirmation = in.readLine();
            	if (!confirmation.equals("done")) {
            		messageArea.append("Problem with upload command.\n");
            	}
            	else {
            		messageArea.append("Upload successful.\n");
            	}
    			
            	
            	inFromFile.close();
            	fileInput.close();
            	
    		} catch (Exception e) {
    			System.out.println("Error uploading file. Error = " + e.toString() + "\n");
    		}
    	}
    }
    
    private static class DownloadThread extends Thread {
    	private InputStream inFromSocket;
    	private String fileName;
    	private JTextArea messageArea;
    	
    	public DownloadThread(String fileName, JTextArea messageArea, InputStream inFromSocket) {
    		this.inFromSocket = inFromSocket;
    		this.fileName = fileName;
    		this.messageArea = messageArea;
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
            	
            	out.println("begin");
            	String confirmation = in.readLine();
            	int length = Integer.parseInt(confirmation);
            	out.println("received");
            	
            	File newFile = new File("./", fileName);
            	FileOutputStream fileOutput = new FileOutputStream(newFile);
            	BufferedOutputStream fileWriter = new BufferedOutputStream(fileOutput);
            	BufferedInputStream inn = new BufferedInputStream(inFromSocket);
            	byte[] bytesFromSocket = new byte[100];
            	int total = 0;
            	int sizeReadFromSocket;
            	
            	while (total != length) {
            		sizeReadFromSocket = inn.read(bytesFromSocket);
            		fileWriter.write(bytesFromSocket, 0, sizeReadFromSocket);
            		total += sizeReadFromSocket;
            	}
            	fileWriter.flush();

            	
            	out.println("done");
            	
            	fileWriter.close();
            	fileOutput.close();
    		} catch (Exception e) {
    			System.out.println("Error downloading file. Error = " + e.toString() + "\n");
    		}
    	}
    }

	public void connectToServer() throws IOException {
        int port;
        //Socket socket;
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
