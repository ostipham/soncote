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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class Server {
	//credit to Necronet for pattern : https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
	private static final Pattern IP_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	
	//technique de creation sur disque
	private static final String rootDirectoryName = "./root";
	
    public static void main(String[] args) throws Exception {
       
        int clientNumber = 0;
        int port;
        String serverAddress;
        
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
        	
        	//information is good -> Instantiate listener and start server.
        	ServerSocket listener;
        	InetAddress locIP = InetAddress.getByName(serverAddress);
        	listener = new ServerSocket();
        	listener.setReuseAddress(true);
        	listener.bind(new InetSocketAddress(locIP, port));
        	
        	
        	if (!(Files.exists(Paths.get(rootDirectoryName)) && Files.isDirectory(Paths.get(rootDirectoryName)))) {
        		Files.createDirectory(Paths.get(rootDirectoryName));
        	}

        	System.out.format("The file management server is running on %s:%d%n", serverAddress, port);
    
        	try {
        		while (true) {
        			new FileManager(listener.accept(), clientNumber++).start();
        		}
        	} finally {
        		listener.close();
        	}
        }
    }

 
    private static class FileManager extends Thread {
        private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    	
    	private Socket socket;
        private int clientNumber;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isThreadRunning;
        private String currentPath;
        

        public FileManager(Socket socket, int clientNumber) {
            this.socket = socket;
            this.clientNumber = clientNumber;
            log("New connection with client# " + clientNumber + " at " + socket);
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                isThreadRunning = true;
                currentPath = rootDirectoryName;

                // Send a welcome message to the client.
                out.println("Hello, you are client #" + clientNumber + ".");
                out.println("Enter a command.\n");
                
                // get client commands
                String[] command;
                while (isThreadRunning) {
                    String input = in.readLine();
                    LocalDateTime date = LocalDateTime.now();
                    log("[" + socket.getInetAddress() + ":" + socket.getPort() + " - " + dateFormat.format(date) + "]: " + input);
                    
                    command = input.split(" ");
                    
                    switch(command[0]) {
                    case "cd" :
                    	executeCdCommand(command[1]);
                    	break;
                    case "ls" :
                    	executeLsCommand();
                    	break;
                    case "mkdir" :
                    	executeMkdirCommand(command[1]);
                    	break;
                    case "upload" :
                    	executeUploadCommand(command[1]);
                    	break;
                    case "download" :
                    	executeDownloadCommand(command[1]);
                    	break;
                    case "exit" :
                    	executeExitCommand();
                    	break;
                    default :
                    	out.println("That is not a valid command. Please enter a valid command.");
                    }
                    command = null;
                }
            } catch (IOException e) {
                log("Error handling client# " + clientNumber + ": " + e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    log("Couldn't close a socket, what's going on?");
                }
                log("Connection with client# " + clientNumber + " closed");
            }
        }

        //FileManager thread private methods
        
        private void log(String message) {
            System.out.println(message);
        }
        
        private void executeCdCommand(String argument) {
        	
        	//checking for valid argument
        	if (argument == null || argument.equals("")) {
        		out.println("This is not a valid argument for command cd.");
        	}
        	
        	// command cd .. -> backing up to parent directory (if possible)
        	if (argument.equals("..") && currentPath.equals("./root")) {
        		currentPath = currentPath.substring(0, currentPath.lastIndexOf("/", 0)); //cutting the last "/name" from path
        		return;
        	}
        	
        	//looking for sub directory for name = argument
        	if (Files.exists(Paths.get(currentPath + "/" + argument)) && Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		currentPath = currentPath + "/" + argument;
        		return;
        	}
        	
        	//No such sub directory
        	out.println("Could not find a sub directory called " + argument);
        }
        
        private void executeLsCommand() throws IOException {
        	ArrayList<String> names = new ArrayList<String>();
        	
        	for (Path sub : Files.newDirectoryStream(Paths.get(currentPath))) {
        		names.add(sub.getFileName().toString());
        	}
        	out.println(names.toString());
        }

        private void executeMkdirCommand(String argument) throws IOException {
        	
        	//check if name is valid for a new directory
        	if (argument.equals(null) || argument.equals("")) {
        		out.println("Enter a non null name for your new directory.");
        		return;
        	}
        	
        	//looking for sub directory for name = argument
        	if (Files.exists(Paths.get(currentPath + "/" + argument)) && Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("There is already a subdirectory with that name.");
        		return;
        	}
        	
        	//Conditions cleared. Creating new directory in current one.
        	Files.createDirectory(Paths.get(currentPath + "/" + argument));
        }
        
        private void executeUploadCommand(String argument) throws IOException {
        	String fileName = argument;
        	int substringIndex;
        	int count = 1;
        	    	
        	//if file already in, register second time as fileName(1).pdf, fileName(2).pdf, etc.
        	while (Files.exists(Paths.get(currentPath + "/" + fileName)) && !Files.isDirectory(Paths.get(currentPath + "/" + fileName))) {
        		substringIndex = fileName.indexOf(".", 0);
        		fileName = fileName.substring(0, substringIndex) + "(" + count++ + ")" + fileName.substring(substringIndex, fileName.length()-1);
        	}
        	
        	//read file checksum client sends so you can verify upload was successful.
        	//String checksum = in.readLine();
        	
        	File newFile = new File(currentPath, fileName); //Adding directory will write file in it
        	OutputStream fileWriter = new FileOutputStream(newFile);
        	InputStream inFromSocket = socket.getInputStream();
        	byte[] bytesFromSocket = new byte[8192];
        	int sizeReadFromSocket;
        	
        	while ((sizeReadFromSocket = inFromSocket.read(bytesFromSocket)) > 0) {
        		fileWriter.write(bytesFromSocket, 0, sizeReadFromSocket);
        	}
        	fileWriter.flush();
        	fileWriter.close();
        	//Do not close the socket input stream. It is still used by BufferedReader in.
        	
        	//Verify if upload worked
        	/*if (!checksum.equals(getFileChecksum(currentPath + "/" + fileName))) {
        		out.println("Upload did not work.");
        	}*/
        }
        
        private void executeDownloadCommand(String argument) throws IOException {
        	if (!Files.exists(Paths.get(currentPath + "/" + argument)) || Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("File does not exist in this directory.");
        		return;
        	}
        	
        	//first get the checksum for the "asked for" file
        	//String checksum = getFileChecksum(currentPath + "/" + argument);
        	//Now send the client the file checksum so he can verify his download later.
        	//out.println(checksum);
        	
        	File fileForClient = new File(currentPath + "/" + argument);
        	InputStream inFromFile = new FileInputStream(fileForClient);
        	OutputStream socketOutput = socket.getOutputStream();
        	byte[] bytesReadFromFile = new byte[8192];
        	int sizeReadFromFile;
        	while ((sizeReadFromFile = inFromFile.read(bytesReadFromFile)) > 0) {
        		socketOutput.write(bytesReadFromFile, 0, sizeReadFromFile);
        	}
        	in.close();
        	//Do not close the socket's output stream. We are still using it with the Printwriter out.
        }
        
        private void executeExitCommand() throws IOException {
        	out.println("Closing connection to server for client " + clientNumber);
        	out.close();
        	in.close();
        	isThreadRunning = false;
        }
        
        private String getFileChecksum(String filePath) throws IOException {
        	String command = "certUtil -hashfile " + filePath + "MD5";
        	Process proc = Runtime.getRuntime().exec(command);
        	
        	BufferedReader commandPromptInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        	String line;
        	String hash = null;
        	
        	while ((line = commandPromptInput.readLine()) != null) {
        		if (!line.contains("MD5") && !line.contains("CertUtil")) {
        			//this is the hash line
        			hash = line;
        			break;
        		}
        	}
        	return hash;
        }
    }
    	
}
