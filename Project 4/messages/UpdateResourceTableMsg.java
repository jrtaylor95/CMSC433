package messages;

import java.util.ArrayList;
import java.util.List;

import enums.UpdateResourceTableType;

public class UpdateResourceTableMsg {
	private final UpdateResourceTableType reason;
	private final List<String> resourceNames;
	
	public UpdateResourceTableMsg(UpdateResourceTableType reason, String resourceName) {
		this.reason = reason;
		resourceNames = new ArrayList<String>();
		resourceNames.add(resourceName);
		
	}
	
	public UpdateResourceTableMsg(UpdateResourceTableType reason, List<String> resourceNames) {
		this.reason = reason;
		this.resourceNames = resourceNames;
	}
	
	public UpdateResourceTableType getReason() {
		return reason;
	}
	
	public List<String> getResourceNames() {
		return resourceNames;
	}
}
