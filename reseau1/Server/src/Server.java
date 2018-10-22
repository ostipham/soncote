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
	
	//credit to Necronet for pattern : https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
	private static final Pattern IPv4_ADDR_PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private static final int IP_ADDRESS_LENGTH = 15;
	private static final int NB_PORT_DIGITS = 4;
	
	//root directory name
	private static final String ROOT_DIRECTORY_NAME = "./root";
	
	private static String ipAddr;
	
	
    public static void main(String[] args) throws Exception {
       
        int clientNumber = 0;
        int port;
        String serverAddress;
        
        // Java swing objects for information input box
        JPanel panel = new JPanel();
        JTextField ipAddrField = new JTextField(IP_ADDRESS_LENGTH);
        JLabel ipAddrLabel = new JLabel("Insert IPv4 address here (format xxx.xxx.xxx.xxx):");
        JTextField portField = new JTextField(NB_PORT_DIGITS);
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
        	ipAddr = serverAddress;
        	
        	//information is good -> Instantiate listener and start server.
        	ServerSocket listener;
        	InetAddress locIP = InetAddress.getByName(serverAddress);
        	listener = new ServerSocket();
        	listener.setReuseAddress(true);
        	listener.bind(new InetSocketAddress(locIP, port));
        	
        	
        	// if not files root directory, create one at server start.
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
                    LocalDateTime date = LocalDateTime.now();
                    log("[" + socket.getInetAddress() + ":" + socket.getPort() + " - " + DATE_FORMAT.format(date) + "]: " + input);
                    
                    command = input.split(" ");
                    
                    switch(command[0]) {
                    case "cd" :
                    	if (command.length == 2)
                    		executeCdCommand(command[1]);
                    	break;
                    case "ls" :
                    	if (command.length == 1)
                    		executeLsCommand();
                    	break;
                    case "mkdir" :
                    	if (command.length == 2)
                    		executeMkdirCommand(command[1]);
                    	break;
                    case "upload" :
                    	if (command.length == 2)
                    		executeUploadCommand(command[1]);
                    	break;
                    case "download" :
                    	if (command.length == 2)
                    		executeDownloadCommand(command[1]);
                    	break;
                    case "exit" :
                    	if (command.length == 1)
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
                    log("Couldn't close the socket.");
                }
                log("Connection with client# " + clientNumber + " closed.");
            }
        }

        
        //FileManager thread private methods  
        private void log(String message) {
            System.out.println(message);
        }
        
        
        /*
         * cd command checks if non null directory name and if directory exists.
         * Changes currentPath to entered directory.
         */
        private void executeCdCommand(String argument) {
        	
        	//checking for valid argument
        	if (argument.equals("") || argument.equals(" ")) {
        		out.println("This is not a valid folder name for command cd.");
        		return;
        	}
        	
        	// command cd .. -> backing up to parent directory (if possible). Not possible from root directory.
        	if (argument.equals("..")) {
        		if (currentPath.equals("./root")) {
        			out.println("Root folder has no parent.");
        			return;
        		}
        		currentPath = currentPath.substring(0, currentPath.lastIndexOf("/", 0)); //cutting the last "/name" from path
        		out.println("You are in " + currentPath + " folder.");
        		return;
        	}
        	
        	//looking for sub directory for name = argument
        	if (Files.exists(Paths.get(currentPath + "/" + argument)) && Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		currentPath = currentPath + "/" + argument;
        		out.println("You are in " + currentPath + " folder.");
        		out.println(RESPONSE_END);
        		return;
        	}
        	
        	//No such sub directory
        	out.println("Could not find a sub directory called " + argument);
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
        	
        	//check if name is valid for a new directory
        	if (argument.equals("") || argument.equals(" ")) {
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
        	out.println("Folder " + argument + " was successfully created.");
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
        	System.out.println(length);
        	System.out.println("\n");
        	out.println("received");
        	
        	File newFile = new File(currentPath, fileName);
        	FileOutputStream fileOutput = new FileOutputStream(newFile);
        	BufferedOutputStream fileWriter = new BufferedOutputStream(fileOutput);
        	InputStream inFromSocket = socket.getInputStream();
        	BufferedInputStream inn = new BufferedInputStream(inFromSocket);
        	byte[] bytesFromSocket = new byte[100];
        	int total = 0;
        	int sizeReadFromSocket;
        	
        	System.out.println("before while...\n");
        	while (total != length) {
        		System.out.println("in while...\n");
        		sizeReadFromSocket = inn.read(bytesFromSocket);
        		fileWriter.write(bytesFromSocket, 0, sizeReadFromSocket);
        		total += sizeReadFromSocket;
        	}
        	fileWriter.flush();
        	System.out.println("after while...\n");
        	
        	
        	System.out.println("done reading...");
        	
        	out.println("done");
        	
        	fileWriter.close();
        	fileOutput.close();
        	
        }
        
        
        /*
         * download command reads from file and writes bytes in socket.
         */
        private void executeDownloadCommand(String argument) throws IOException {
        	/*
        	if (!Files.exists(Paths.get(currentPath + "/" + argument)) || Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("File does not exist in this directory.");
        		return;
        	}
        	
        	File fileForServer = new File(currentPath + "/" + argument);
        	FileInputStream fileInput = new FileInputStream(fileForServer);
        	BufferedInputStream inFromFile = new BufferedInputStream(fileInput);
        	byte[] bytesReadFromFile = new byte[(int) fileForServer.length()];
        	int sizeReadFromFile;
        	OutputStream outToSocket = socket.getOutputStream();
        	
        	while ((sizeReadFromFile = inFromFile.read(bytesReadFromFile)) > 0) {
        		outToSocket.write(bytesReadFromFile, 0, sizeReadFromFile);
        	}
        	
        	outToSocket.flush();
        	inFromFile.close();
        	fileInput.close();
        	
        	out.println("The file " + argument + " was successfully downloaded.");
        	out.println(RESPONSE_END);
        	*/
        	ServerSocket listener;
        	InetAddress locIP = InetAddress.getByName(ipAddr);
        	listener = new ServerSocket();
        	listener.setReuseAddress(true);
        	listener.bind(new InetSocketAddress(locIP, 5555));
        	Socket socket = listener.accept();
        	
        	if (!Files.exists(Paths.get(currentPath + "/" + argument)) || Files.isDirectory(Paths.get(currentPath + "/" + argument))) {
        		out.println("File does not exist in this directory.");
        		listener.close();
        		return;
        	}
        	
        	File fileForServer = new File(currentPath + "/" + argument);
        	FileInputStream fileInput = new FileInputStream(fileForServer);
        	BufferedInputStream inFromFile = new BufferedInputStream(fileInput);
        	byte[] bytesReadFromFile = new byte[(int) fileForServer.length()];
        	int sizeReadFromFile;
        	OutputStream outToSocket = socket.getOutputStream();
        	
        	while ((sizeReadFromFile = inFromFile.read(bytesReadFromFile)) > 0) {
        		outToSocket.write(bytesReadFromFile, 0, sizeReadFromFile);
        	}
        	
        	outToSocket.flush();
        	inFromFile.close();
        	fileInput.close();
        	listener.close();
        	
        	out.println("The file " + argument + " was successfully downloaded.");
        	out.println(RESPONSE_END);
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
