import java.io.File;
import java.util.ArrayList;

public class Folder {
	private Folder parentDirectory;
	private ArrayList<Folder> childrenDirectories;
	private ArrayList<File> files;
	private String folderName;
	private boolean safeToModify;
	
	public Folder(String folderName, Folder parentDirectory) {
		this.folderName = folderName;
		System.out.println(folderName);
		this.parentDirectory = parentDirectory;
		this.safeToModify = true;
		this.childrenDirectories = new ArrayList<Folder>();
		this.files = new ArrayList<File>();
	}
	
	//getters
	public Folder getParentDirectory() {
		return this.parentDirectory;
	}
	
	public ArrayList<Folder> getChildrenDirectories() {
		return this.childrenDirectories;
	}
	
	public ArrayList<File> getFiles() {
		return this.files;
	}
	
	public String getFolderName() {
		return this.folderName;
	}
	
	public boolean isSafeToModify() {
		return safeToModify;
	}
	
	public void setSafeToModify(boolean state) {
		safeToModify = state;
	}
	
}
