package messages;

public class ResourcePingMsg {
	private final String resourceName;
	
	public ResourcePingMsg(String resourceName) {
		this.resourceName = resourceName;
	}
	
	public String getResourceName() {
		return resourceName;
	}
	
	@Override 
	public String toString () {
		return  "request for " + resourceName;
	}
}
