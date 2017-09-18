package messages;

import java.util.LinkedList;

import util.Resource;

public class ResourceListResponseMsg {
	private final LinkedList<Resource> resourceList;
	
	public ResourceListResponseMsg(LinkedList<Resource> resourceList) {
		this.resourceList = resourceList;
	}
	
	public LinkedList<Resource> getResourceList() {
		return resourceList;
	}
}
