package actors;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import messages.*;
import util.*;
import enums.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class ResourceManagerActor extends UntypedActor {

	private ActorRef logger;					// Actor to send logging messages to

	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}

	/**
	 * Factory method for creating resource managers
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}

	/**
	 * Method for logging receive
	 * 
	 * @param msg	Message received
	 */
	private void logReceive(Object msg) {
		logger.tell(LogMsg.makeReceiveLogMsg(getSender(), msg, getSelf()), getSelf());
	}

	/**
	 * Method for logging send of message.
	 * 
	 * @param msf	Message sent
	 * @param recipient	Recipient of message
	 */	
	private void logSend(Object msg, ActorRef recipient) {
		logger.tell(LogMsg.makeSendLogMsg(getSelf(), msg, recipient), getSelf());
	}

	/**
	 * Constructor
	 * 
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	// You may want to add data structures for managing local resources and users, storing
	// remote managers, etc.

	//Structures for user access
	UserLockHashMap exclusiveWriteAccessMap = new UserLockHashMap();
	UserLockHashMap concurrentReadAccessMap = new UserLockHashMap();

	//Structures for resources
	ConcurrentHashMap<String, Resource> localResources = new ConcurrentHashMap<String, Resource>();
	ConcurrentHashMap<String, ActorRef> localResourceManagerMap = new ConcurrentHashMap<String, ActorRef>();

	ConcurrentHashMap.KeySetView<ActorRef, Boolean> localUsers = ConcurrentHashMap.newKeySet();
	PendingRequestMap pendingRequests = new PendingRequestMap();
	ConcurrentHashMap.KeySetView<ActorRef, Boolean> remoteManagers = ConcurrentHashMap.newKeySet();

	/* (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive method below.
	 * 
	 * @see akka.actor.UntypedActor#onReceive(java.lang.Object)
	 */
	@Override
	public void onReceive(Object msg) throws Exception {
		// Don't forget to enable all the resources when processing the AddInitialLocalResourcesRequestMsg
		logReceive(msg);

		if (msg instanceof AccessRequestMsg) {
			AccessRequestMsg aMsg = (AccessRequestMsg) msg;
			processAccessRequest(aMsg);
		} else if (msg instanceof AccessReleaseMsg) {
			AccessReleaseMsg aMsg = (AccessReleaseMsg) msg;
			processAccessRelease(aMsg);
		} else if (msg instanceof ManagementRequestMsg) {
			ManagementRequestMsg mMsg = (ManagementRequestMsg) msg;
			processManagementRequest(mMsg);
		} else if (msg instanceof AddInitialLocalResourcesRequestMsg) {
			AddInitialLocalResourcesRequestMsg aMsg = (AddInitialLocalResourcesRequestMsg) msg;
			processAddInitialLocalResourcesRequest(aMsg);
		} else if (msg instanceof AddLocalUsersRequestMsg) {
			AddLocalUsersRequestMsg aMsg = (AddLocalUsersRequestMsg) msg;
			processAddLocalUsersRequest(aMsg);
		} else if (msg instanceof UpdateResourceTableMsg) {
			UpdateResourceTableMsg uMsg = (UpdateResourceTableMsg) msg;
			processUpdateResourceTable(uMsg);
		} else if (msg instanceof AddRemoteManagersRequestMsg) {
			AddRemoteManagersRequestMsg aMsg = (AddRemoteManagersRequestMsg) msg;
			processAddRemoteManagersRequest(aMsg);
		} else if (msg instanceof ResourceListRequestMsg) {
			ResourceListRequestMsg rMsg = (ResourceListRequestMsg) msg;
			processResourceListRequest(rMsg);
		} else if (msg instanceof ResourceListResponseMsg) {
			ResourceListResponseMsg rMsg = (ResourceListResponseMsg) msg;
			processResourceListResponse(rMsg);
		}

	}

	//Initialization requests

	private void processAddInitialLocalResourcesRequest(AddInitialLocalResourcesRequestMsg msg) {
		ArrayList<Resource> resources = new ArrayList<Resource>(msg.getLocalResources());

		for (Resource resource : resources) {
			String resourceName = resource.getName();

			resource.enable();
			localResources.put(resourceName, resource);
			exclusiveWriteAccessMap.addResource(resourceName);
			concurrentReadAccessMap.addResource(resourceName);
			pendingRequests.addResource(resourceName);
		}

		sendMsg(new AddInitialLocalResourcesResponseMsg(msg), getSender());
	}

	private void processAddLocalUsersRequest(AddLocalUsersRequestMsg msg) {
		localUsers.addAll(msg.getLocalUsers());

		sendMsg(new AddLocalUsersResponseMsg(msg), getSender());
	}

	//Access requests

	private void processAccessRequest(AccessRequestMsg msg) {
		AccessRequest request = msg.getAccessRequest();
		String resourceName = request.getResourceName();
		AccessRequestType type = request.getType();
		ActorRef user = msg.getReplyTo();

		boolean isLocal = localResources.containsKey(resourceName);

		ReturnCode result;

		if (isLocal) {
			processPendingRequests(resourceName);
			if (pendingRequests.size(resourceName) > 0) {
				switch(type) {
				case EXCLUSIVE_WRITE_BLOCKING: result = ReturnCode.PENDING; break;
				case EXCLUSIVE_WRITE_NONBLOCKING: result = ReturnCode.BUSY; break;
				case CONCURRENT_READ_BLOCKING: result = ReturnCode.PENDING; break;
				case CONCURRENT_READ_NONBLOCKING: result = ReturnCode.BUSY; break;
				default: result = ReturnCode.INVALID; break;
				}
			} else {
				Resource resource = localResources.get(resourceName);

				if (resource.getStatus() == ResourceStatus.DISABLED) {
					result = ReturnCode.DISABLED;
				} else if (pendingRequests.size(resourceName) > 0 && pendingRequests.peekRequest(resourceName) instanceof ManagementRequestMsg) {
					result = ReturnCode.DISABLED;
				} else {
					switch (type) {
					case EXCLUSIVE_WRITE_BLOCKING: result = giveExclusiveWriteAccessBlocking(resourceName, user); break;
					case EXCLUSIVE_WRITE_NONBLOCKING: result = giveExclusiveWriteAccessNonBlocking(resourceName, user); break;
					case CONCURRENT_READ_BLOCKING: result = giveConcurrentReadAccessBlocking(resourceName, user); break;
					case CONCURRENT_READ_NONBLOCKING: result = giveConcurrentReadAccessNonBlocking(resourceName, user); break;
					default: result = ReturnCode.INVALID;
					}
				}
			}
		} else {
			if (localResourceManagerMap.containsKey(resourceName)) {
				sendMsg(msg, localResourceManagerMap.get(resourceName));
				result = ReturnCode.FORWARDED;
			} else {
				result = ReturnCode.NOT_FOUND;
			}
		}

		switch (result) {
		case SUCCESS: sendMsg(new AccessRequestGrantedMsg(request), user); break;
		case BUSY: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_BUSY), user); break;
		case DISABLED: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_DISABLED), user); break;
		case NOT_FOUND: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_NOT_FOUND), user); break;
		case PENDING: pendingRequests.addRequest(msg, resourceName); break;
		default: break;
		}

	}

	private int processPendingAccessRequest(AccessRequestMsg msg) {
		AccessRequest request = msg.getAccessRequest();
		String resourceName = request.getResourceName();
		AccessRequestType type = request.getType();
		ActorRef user = msg.getReplyTo();

		boolean isLocal = localResources.containsKey(resourceName);

		ReturnCode result;

		if (isLocal) {
			Resource resource = localResources.get(resourceName);

			if (resource.getStatus() == ResourceStatus.DISABLED) {
				result = ReturnCode.DISABLED;
			} else if (pendingRequests.size(resourceName) > 0 && pendingRequests.peekRequest(resourceName) instanceof ManagementRequestMsg){
				result = ReturnCode.DISABLED;
			} else {
				switch (type) {
				case EXCLUSIVE_WRITE_BLOCKING: result = giveExclusiveWriteAccessBlocking(resourceName, user); break;
				case EXCLUSIVE_WRITE_NONBLOCKING: result = giveExclusiveWriteAccessNonBlocking(resourceName, user); break;
				case CONCURRENT_READ_BLOCKING: result = giveConcurrentReadAccessBlocking(resourceName, user); break;
				case CONCURRENT_READ_NONBLOCKING: result = giveConcurrentReadAccessNonBlocking(resourceName, user); break;
				default: result = ReturnCode.INVALID;
				}
			}
		} else {
			if (localResourceManagerMap.containsKey(resourceName)) {
				sendMsg(msg, localResourceManagerMap.get(resourceName));
				result = ReturnCode.FORWARDED;
			} else {
				result = ReturnCode.NOT_FOUND;
			}
		}

		switch (result) {
		case SUCCESS: sendMsg(new AccessRequestGrantedMsg(request), user); return 0;
		case BUSY: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_BUSY), user); return 1;
		case DISABLED: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_DISABLED), user); return 1;
		case NOT_FOUND: sendMsg(new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_NOT_FOUND), user); return 1;
		case PENDING: pendingRequests.addRequest(msg, resourceName); return 1;
		default: return 1;
		}

	}
	private ReturnCode giveExclusiveWriteAccessBlocking(String resourceName, ActorRef user) {
		//Check if there are users reading
		if (concurrentReadAccessMap.hasUsers(resourceName)) {
			//Check if nobody else is reading
			if (concurrentReadAccessMap.isOnlyUser(user, resourceName)) {
				//Check if nobody else is writing
				if (!exclusiveWriteAccessMap.hasUsers(resourceName)) {
					exclusiveWriteAccessMap.addUser(user, resourceName);
					return ReturnCode.SUCCESS;
				} else {
					//Check if access is re-entrant. If not, add to pending list
					if (exclusiveWriteAccessMap.isOnlyUser(user,  resourceName)) {
						exclusiveWriteAccessMap.addUser(user, resourceName);
						return ReturnCode.SUCCESS;
					} else {
						return ReturnCode.PENDING;
					}
				}
			} else {
				return ReturnCode.PENDING;
			}
		} else {
			//Check if nobody else is writing
			if (!exclusiveWriteAccessMap.hasUsers(resourceName)) {
				exclusiveWriteAccessMap.addUser(user, resourceName);
				return ReturnCode.SUCCESS;
			} else {
				//Check if access is re-entrant. If not, add to pending list
				if (exclusiveWriteAccessMap.isOnlyUser(user, resourceName)) {
					exclusiveWriteAccessMap.addUser(user, resourceName);
					return ReturnCode.SUCCESS;
				} else {
					return ReturnCode.PENDING;
				}
			}
		}
	}

	private ReturnCode giveExclusiveWriteAccessNonBlocking(String resourceName, ActorRef user) {
		if (pendingRequests.size(resourceName) > 0) {
			return ReturnCode.BUSY;
		}

		//Check if there are users reading
		if (concurrentReadAccessMap.hasUsers(resourceName)) {
			//Check if nobody else is reading
			if (concurrentReadAccessMap.isOnlyUser(user, resourceName)) {
				//Check if nobody else is writing
				if (!exclusiveWriteAccessMap.hasUsers(resourceName)) {
					exclusiveWriteAccessMap.addUser(user, resourceName);
					return ReturnCode.SUCCESS;
				} else {
					//Check if access is re-entrant. If not, add to pending list
					if (exclusiveWriteAccessMap.isOnlyUser(user,  resourceName)) {
						exclusiveWriteAccessMap.addUser(user, resourceName);
						return ReturnCode.SUCCESS;
					} else {
						return ReturnCode.BUSY;
					}
				}
			} else {
				return ReturnCode.BUSY;
			}
		} else {
			//Check if nobody else is writing
			if (!exclusiveWriteAccessMap.hasUsers(resourceName)) {
				exclusiveWriteAccessMap.addUser(user, resourceName);
				return ReturnCode.SUCCESS;
			} else {
				//Check if access is re-entrant. If not, add to pending list
				if (exclusiveWriteAccessMap.isOnlyUser(user,  resourceName)) {
					exclusiveWriteAccessMap.addUser(user, resourceName);
					return ReturnCode.SUCCESS;
				} else {
					return ReturnCode.BUSY;
				}
			}
		}
	}

	private ReturnCode giveConcurrentReadAccessBlocking(String resourceName, ActorRef user) {
		if (pendingRequests.size(resourceName) > 0) {
			return ReturnCode.PENDING;
		}

		//Check if there are users writing
		if (exclusiveWriteAccessMap.hasUsers(resourceName)) {
			//Check if user is already writing
			if (exclusiveWriteAccessMap.isOnlyUser(user, resourceName)) {
				concurrentReadAccessMap.addUser(user, resourceName);
				return ReturnCode.SUCCESS;
			} else {
				return ReturnCode.PENDING;
			}
		} else {
			concurrentReadAccessMap.addUser(user, resourceName);
			return ReturnCode.SUCCESS;
		}
	}

	private ReturnCode giveConcurrentReadAccessNonBlocking(String resourceName, ActorRef user) {
		if (pendingRequests.size(resourceName) > 0) {
			return ReturnCode.BUSY;
		}

		//Check if there are users writing
		if (exclusiveWriteAccessMap.hasUsers(resourceName)) {
			//Check if user is already writing
			if (exclusiveWriteAccessMap.isOnlyUser(user, resourceName)) {
				concurrentReadAccessMap.addUser(user, resourceName);
				return ReturnCode.SUCCESS;
			} else {
				return ReturnCode.BUSY;
			}
		} else {
			concurrentReadAccessMap.addUser(user, resourceName);
			return ReturnCode.SUCCESS;
		}
	}

	//Access release methods

	private void processAccessRelease(AccessReleaseMsg msg) {
		AccessRelease release = msg.getAccessRelease();
		String resourceName = release.getResourceName();
		AccessType type = release.getType();
		ActorRef user = msg.getSender();

		boolean isLocal = localResources.containsKey(resourceName);

		if (isLocal) {
			switch (type) {
			case EXCLUSIVE_WRITE: revokeExclusiveWriteAccess(resourceName, user); break;
			case CONCURRENT_READ: revokeConcurrentReadAccess(resourceName, user); break;
			default: break;
			}
		} else {
			//Forward message
			if (localResourceManagerMap.containsKey(resourceName)) {
				sendMsg(msg, localResourceManagerMap.get(resourceName));
			}
		}

		processPendingRequests(resourceName);
	}

	private void revokeExclusiveWriteAccess(String resourceName, ActorRef user) {
		//Check if user has write access
		if (exclusiveWriteAccessMap.hasUser(user, resourceName)) {
			exclusiveWriteAccessMap.removeUser(user, resourceName);
		}
	}

	private void revokeConcurrentReadAccess(String resourceName, ActorRef user) {
		//Check if the user has read access
		if (concurrentReadAccessMap.hasUser(user, resourceName)) {
			concurrentReadAccessMap.removeUser(user, resourceName);
		}
	}

	//management requests

	private void processManagementRequest(ManagementRequestMsg msg) {
		ManagementRequest request = msg.getRequest();
		ActorRef user = msg.getReplyTo();
		ManagementRequestType type = request.getType();
		String resourceName = request.getResourceName();

		ReturnCode result = ReturnCode.SUCCESS;
		boolean isLocal = true;
		if (type != ManagementRequestType.ADD) {
			isLocal = localResources.containsKey(resourceName);
		}

		if (isLocal) {
			switch (type) {
			case DISABLE: result = disableResource(resourceName, user, msg); break;
			case ENABLE: enableResource(resourceName, msg); break;
			case REMOVE: removeLocalResource(resourceName); break;
			case ADD: result = addLocalResource(resourceName); break;
			default: result = ReturnCode.INVALID;
			}
		} else {
			if (type == ManagementRequestType.DISABLE || type == ManagementRequestType.ENABLE) {
				if (localResourceManagerMap.containsKey(resourceName)) {
					sendMsg(msg, localResourceManagerMap.get(resourceName));
					result = ReturnCode.FORWARDED;
				} else {
					result = ReturnCode.NOT_FOUND;
				}
			} else {
				result = ReturnCode.NOT_LOCAL;
			}
		}

		switch (result) {
		case SUCCESS: sendMsg(new ManagementRequestGrantedMsg(request), user); 
		if (type == ManagementRequestType.ADD)
			broadcast(new UpdateResourceTableMsg(UpdateResourceTableType.ADD, resourceName)); 
		else if (type == ManagementRequestType.REMOVE)
			broadcast(new UpdateResourceTableMsg(UpdateResourceTableType.REMOVE, resourceName));
		break;
		case NOT_LOCAL: sendMsg(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.RESOURCE_NOT_LOCAL), user); break;
		case NOT_FOUND: sendMsg(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.RESOURCE_NOT_FOUND), user); break;
		case ACCESS_HELD: sendMsg(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.ACCESS_HELD_BY_USER), user); break;
		case NOT_DISABLED: sendMsg(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.RESOURCE_NOT_DISABLED), user); break;
		case NAME_CLASH: sendMsg(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.RESOURCE_NAME_CLASH), user); break;
		default: break;
		}
	}

	/*
	 * Disables an enabled resource, making it unavailable to users.
	 * Successive calls to a disabled resource are ignored, only returning a "disabled" message.
	 * May be used on remote machines.
	 */
	private ReturnCode disableResource(String resourceName, ActorRef user, ManagementRequestMsg msg) {
		if (localResources.get(resourceName).getStatus() != ResourceStatus.DISABLED) {
			if (exclusiveWriteAccessMap.hasUser(user, resourceName) || concurrentReadAccessMap.hasUser(user, resourceName)) 
				return ReturnCode.ACCESS_HELD;

			//Send disabled messages to all users with pending blocking requests
			for (Object m : pendingRequests.getRequests(resourceName)) {
				AccessRequestMsg aMsg = (AccessRequestMsg) m;
				sendMsg(new AccessRequestDeniedMsg(aMsg.getAccessRequest(), AccessRequestDenialReason.RESOURCE_DISABLED), aMsg.getReplyTo());
			}

			LinkedList<Object> disableMsg = new LinkedList<Object>();
			disableMsg.add(msg);
			pendingRequests.replace(disableMsg, resourceName);
		}

		return ReturnCode.SUCCESS;
	}

	/*
	 * Enables a disabled resource, making it available to other users.
	 * Successive calls to an enabled resource are ignored, only returning an "enabled" message.
	 * May be used on remote machines.
	 */
	private void enableResource(String resourceName, ManagementRequestMsg msg) {

		Resource resource = localResources.get(resourceName);
		if (pendingRequests.size(resourceName) > 0) {
			if (pendingRequests.peekRequest(resourceName) instanceof ManagementRequestMsg) {
				pendingRequests.addRequest(msg, resourceName);
			}
		} else if (resource.getStatus() != ResourceStatus.ENABLED) {
			resource.enable();
			pendingRequests.addResource(resourceName);
		}
	}

	/*
	 * Removes a resource from a local machine.
	 * May only be used on on a local machine.
	 */
	private void removeLocalResource(String resourceName) {
		if (localResources.get(resourceName).getStatus() == ResourceStatus.DISABLED) {
			localResources.remove(resourceName);
			pendingRequests.removeResource(resourceName);
		}
	}

	/*
	 *  Adds a resource to the local machine.
	 *  May only be used on a local machine.
	 */
	private ReturnCode addLocalResource(String resourceName) {
		if (!localResources.containsKey(resourceName) && !localResourceManagerMap.containsKey(resourceName)) {
			Resource resource = Systems.makeResource(resourceName);
			localResources.put(resourceName, resource);
			pendingRequests.addResource(resourceName);

			return ReturnCode.SUCCESS;
		} else {
			return ReturnCode.NAME_CLASH;
		}
	}

	//Update resource table methods

	private void processUpdateResourceTable(UpdateResourceTableMsg msg) {
		UpdateResourceTableType reason = msg.getReason();
		List<String> resourceNames = msg.getResourceNames();
		ActorRef manager = getSender();
		switch (reason) {
		case ADD: addRemoteResource(resourceNames, manager); break;
		case REMOVE: removeRemoteResource(resourceNames); break;
		}
	}

	private void addRemoteResource(List<String> resourceNames, ActorRef manager) {
		for (String resourceName : resourceNames) {
			localResourceManagerMap.put(resourceName, manager);
		}
	}

	private void removeRemoteResource(List<String> resourceNames) {
		for (String resourceName : resourceNames) {
			localResourceManagerMap.remove(resourceName);
		}
	}

	private void processAddRemoteManagersRequest(AddRemoteManagersRequestMsg msg) {
		ArrayList<ActorRef> resourceManagers = msg.getManagerList();

		for (ActorRef manager : resourceManagers) {
			if (!remoteManagers.contains(manager)) {
				if (!manager.equals(getSelf())) {
					remoteManagers.add(manager);
				}
			}
		}

		broadcast(new ResourceListRequestMsg());
		sendMsg(new AddRemoteManagersResponseMsg(msg), getSender());
	}

	//Get resource list methods
	private void processResourceListRequest(ResourceListRequestMsg msg) {
		LinkedList<Resource> resourceList = new LinkedList<Resource>();

		for (String resourceName : localResources.keySet()) {
			resourceList.add(localResources.get(resourceName));
		}

		sendMsg(new ResourceListResponseMsg(resourceList), getSender());
	}

	private void processResourceListResponse(ResourceListResponseMsg msg) {
		LinkedList<Resource> resourceList = msg.getResourceList();

		for (Resource resource : resourceList) {
			localResourceManagerMap.put(resource.getName(), getSender());
		}
	}

	//Util methods

	private void sendMsg(Object msg, ActorRef recipient) {
		recipient.tell(msg, getSelf());
		logSend(msg, recipient);
	}


	private void broadcast(Object msg) {
		for (ActorRef manager : remoteManagers) {
			sendMsg(msg, manager);
		}
	}

	private void processPendingRequests(String resourceName) {
		//While there are still pending requests
		while (pendingRequests.size(resourceName) > 0) {
			Object request = pendingRequests.getRequest(resourceName);

			//process the access request
			if (request instanceof AccessRequestMsg) {
				AccessRequestMsg pendingRequest = (AccessRequestMsg) request;

				if (processPendingAccessRequest(pendingRequest) == 1) {
					//Remove duplicate request
					pendingRequests.getLatestRequest(resourceName);
					pendingRequests.makeEarliestRequest(pendingRequest, resourceName);
					break;
				}
			} else {

				//process the management request
				if (!exclusiveWriteAccessMap.hasUsers(resourceName) && !concurrentReadAccessMap.hasUsers(resourceName)) {
					processManagementRequest((ManagementRequestMsg) request);
				} else {
					break;
				}
			}
		}
	}
}
