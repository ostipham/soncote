import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
	private static final Pattern IPv4_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private static final int NB_DIGITS_IP_ADDRESS = 15;
	private static final int NB_DIGITS_PORT = 4;
	
	//root directory name
	private static final String ROOT_DIRECTORY_NAME = "./root";
	
	
    public static void main(String[] args) throws Exception {
       
        int clientNumber = 0;
        int port;
        String serverAddress;
        
        // Java swing objects for information input box
        JPanel panel = new JPanel();
        JTextField ipAddrField = new JTextField(NB_DIGITS_IP_ADDRESS);
        JLabel ipAddrLabel = new JLabel("Insert IPv4 address here (format xxx.xxx.xxx.xxx):");
        JTextField portField = new JTextField(NB_DIGITS_PORT);
        JLabel portLabel = new JLabel("Insert port here (between 5000 and 5050):");
        panel.add(ipAddrLabel);
        panel.add(ipAddrField);
        panel.add(portLabel);
        panel.add(portField);
        
        
        // Get the server address and the port from a dialog box.
        int ok = JOptionPane.showConfirmDialog(null, panel,"Enter Server informations:", JOptionPane.OK_CANCEL_OPTION);    
        if (ok == JOptionPane.OK_OPTION) {     
        	serverAddress = ipAddrField.getText();
        	try {
        		port = Integer.parseInt(portField.getText());
        	} catch (NumberFormatException e) {
        		// port entered is not a number
        		port = 0;
        	}
        	
        	
        	// verification loop
        	while (!(port >= 5000 && port <= 5050 && IPv4_ADDR_PATTERN.matcher(serverAddress).matches())) {
        		ok = JOptionPane.showConfirmDialog(null, panel,"Some informations were faulty. Enter Server informations:", 
        									  	   JOptionPane.OK_CANCEL_OPTION);
        		if (ok == JOptionPane.CANCEL_OPTION)
        			return;
        		serverAddress = ipAddrField.getText();
        		try {
            		port = Integer.parseInt(portField.getText());
            	} catch (NumberFormatException e) {
            		// port entered is not a number
            		port = 0;
            	}
        	}
        	
        	//information is good -> Instantiate listener and start server.
        	ServerSocket listener;
        	InetAddress locIP = InetAddress.getByName(serverAddress);
        	listener = new ServerSocket();
        	listener.setReuseAddress(true);
        	listener.bind(new InetSocketAddress(locIP, port));
        	
        	
        	// if no root directory, create one at server start.
        	if (!(Files.exists(Paths.get(ROOT_DIRECTORY_NAME)) && Files.isDirectory(Paths.get(ROOT_DIRECTORY_NAME)))) {
        		Files.createDirectory(Paths.get(ROOT_DIRECTORY_NAME));
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
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    	
    	private Socket socket;
        private int clientNumber;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isThreadRunning;
        
        // The directory path the client is currently at. Ex : ./root/polymtl/inf3405/
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
                currentPath = ROOT_DIRECTORY_NAME; //clients connecting to server start at root directory

                // Send a welcome message to the client.
                out.println("Hello, you are client #" + clientNumber + ".");
                out.println("Enter a command.\n");
                
                // get client commands
                String[] command;
                while (isThreadRunning) {
                    String input = in.readLine(); 
                    command = input.split(" ");
                    
                    //Checks the validity of command's number of words. Ex: If client command = ls, should be 1 word.
                    if (!checkCommandValidity(command)) {
                    	command[0] = INVALID_COMMAND;
                    } else {
                    	//if valid, print command in server console
                    	LocalDateTime date = LocalDateTime.now();
                    	log("[" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " - " + DATE_FORMAT.format(date) + "]: " + input);
                    }
                    	
                    
                    switch(command[0]) {
                    case CD :
                    	executeCdCommand(command[1]);
                    	break;
                    case LS :
                    	executeLsCommand();
                    	break;
                    case MKDIR :
                    	executeMkdirCommand(command[1]);
                    	break;
                    case UPLOAD :
                    	executeUploadCommand(command[1]);
                    	break;
                    case DOWNLOAD :
                    	executeDownloadCommand(command[1]);
                    	break;
                    case EXIT :
                    	executeExitCommand();
                    	break;
                    default :
                    	out.println("That is not a valid command. Please enter a valid command.");
                    	out.println(RESPONSE_END);
                    }
                    command = null;
                }
            } catch (IOException e) {
                log("Error handling client# " + clientNumber + ": " + e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    log("Couldn't close the socket.");
                }
                log("Connection with client# " + clientNumber + " closed.");
            }
        }

        
        //FileManager thread private methods  
        private void log(String message) {
            System.out.println(message);
        }
        
        private boolean checkCommandValidity(String[] command) {
        	if (command[0].equals(LS) || command[0].equals(EXIT))
        		return (command.length == 1);
        	
        	if (command[0].equals(MKDIR) || command[0].equals(CD) || command[0].equals(UPLOAD) || command[0].equals(DOWNLOAD))
        		return (command.length == 2);
        		
        	return false;
        }
        
        
        /*
         * cd command checks if non null directory name and if directory exists.
         * Changes currentPath to entered directory.
         */
        private void executeCdCommand(String argument) {
        	log(argument + "\n");
        	// command cd .. -> backing up to parent directory (if possible). Not possible from root directory.
        	if (argument.equals(BACK) && currentPath.equals(ROOT_DIRECTORY_NAME)) {
        		out.println("Root folder has no parent.");
        	} else if (argument.equals(BACK)) {
        		log("in .. possible if\n");
        		currentPath = (currentPath.lastIndexOf("/", 0) != -1)?
        				currentPath.substring(0, currentPath.lastIndexOf("/", 0)) : ROOT_DIRECTORY_NAME; 
        		out.println("You are in " + currentPath + " folder.");
        	} else if (!Files.exists(Paths.get(currentPath + "/" + argument)) || !Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		//check if file exists and is a folder
        		out.println("Could not find a sub directory called " + argument);
        	} else {
        		currentPath = currentPath + "/" + argument;
        		out.println("You are in " + currentPath + " folder.");
        	}

        	out.println(RESPONSE_END);
        }
        
        
        /*
         * ls command sorts files and sub-directories and send them to client
         */
        private void executeLsCommand() throws IOException {
        	ArrayList<String> fileNames = new ArrayList<String>();
        	ArrayList<String> directoryNames = new ArrayList<String>();
        	
        	//sort by type
        	for (Path sub : Files.newDirectoryStream(Paths.get(currentPath))) {
        		if (sub.toFile().isFile()) {
        			fileNames.add(sub.getFileName().toString());
        		}	
        		else {
        			directoryNames.add(sub.getFileName().toString());
        		}	
        	}

        	// send to client
        	for (String name: directoryNames) {
        		out.println("[Folder] " + name);
        	}
        	for (String name: fileNames) {
        		out.println("[File] " + name);
        	}
        	
        	out.println(RESPONSE_END);
        }

        
        /*
         * mkdir command checks if name is valid and if sub-directory doesn't exist then creates sub-directory.
         */
        private void executeMkdirCommand(String argument) throws IOException {
        	//check if a folder with this name already exists
        	if (Files.exists(Paths.get(currentPath + "/" + argument)) && Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("There is already a subdirectory with that name.");
        	} else {
        		//Conditions cleared. Creating new directory in current one.
        		Files.createDirectory(Paths.get(currentPath + "/" + argument));
        		out.println("Folder " + argument + " was successfully created.");
        	}
        	
        	out.println(RESPONSE_END);
        }
        
        
        /*
         * upload command renames if already existing file present, reads from socket and write in file.
         */
        private void executeUploadCommand(String argument) throws IOException {     	  	
        	String fileName = argument;
        	int substringIndex;
        	int count = 1;
        	    	
        	//if file already in, register second time as fileName(1).pdf, fileName(2).pdf, etc.
        	if (Files.exists(Paths.get(currentPath + "/" + fileName)) && !Files.isDirectory(Paths.get(currentPath + "/" + fileName))) {
        		substringIndex = fileName.indexOf(".", 0);
        		fileName = fileName.substring(0, substringIndex) + "(" + count + ")" + fileName.substring(substringIndex, fileName.length());
        	}
        	while (Files.exists(Paths.get(currentPath + "/" + fileName)) && !Files.isDirectory(Paths.get(currentPath + "/" + fileName))) {
        		fileName = fileName.replace("(" + count + ")", "(" + ++count + ")");
        	}
        	
        	out.println("begin");
        	String confirmation = in.readLine();
        	int length = Integer.parseInt(confirmation);
        	out.println("received");
        	
        	File newFile = new File(currentPath, fileName);
        	FileOutputStream fileOutput = new FileOutputStream(newFile);
        	BufferedOutputStream fileWriter = new BufferedOutputStream(fileOutput);
        	InputStream inFromSocket = socket.getInputStream();
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

        	out.println(RESPONSE_END);	
        	fileWriter.close();
        	fileOutput.close();
        	
        }
        
        
        /*
         * download command reads from file and writes bytes in socket.
         */
        private void executeDownloadCommand(String argument) throws IOException {
        	if (!Files.exists(Paths.get(currentPath + "/" + argument)) || Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("The requested file does not exist.");
        		out.println(RESPONSE_END);	
        		return;
        	}
        	
        	String confirmation = in.readLine();
			if (!confirmation.equals("begin"))
				return;

			File fileForClient = new File(currentPath + "/" + argument);
			int length = (int) fileForClient.length();
			out.println(length);
			confirmation = in.readLine();
			if (!confirmation.equals("received"))
				return;
			
        	FileInputStream fileInput = new FileInputStream(fileForClient);
        	BufferedInputStream inFromFile = new BufferedInputStream(fileInput);
        	byte[] bytesReadFromFile = new byte[100];
        	int sizeReadFromFile;
        	int total = 0;
        	BufferedOutputStream outt = new BufferedOutputStream(socket.getOutputStream());
        	
        	while (total != length) {
        		sizeReadFromFile = inFromFile.read(bytesReadFromFile);
        		outt.write(bytesReadFromFile, 0, sizeReadFromFile);
        		total += sizeReadFromFile;
        	}
        	outt.flush();
        	
        	confirmation = in.readLine();
        	if (!confirmation.equals("done")) {
        		out.println("Problem with download command.\n");
        	}
        	else {
        		out.println("Download successful.\n");
        	}
			
        	out.println(RESPONSE_END);
        	inFromFile.close();
        	fileInput.close();
        }
        
        
        /*
         * Closing connection with client.
         */
        private void executeExitCommand() throws IOException {
        	out.println("Closing connection to server for client " + clientNumber);
        	out.close();
        	in.close();
        	isThreadRunning = false;
        }     
    }
    	
}
