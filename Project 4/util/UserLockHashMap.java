package util;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;

public class UserLockHashMap {

	private ConcurrentHashMap<String, HashMap<String, Integer>> userLockMap = new ConcurrentHashMap<String, HashMap<String, Integer>>();

	
	//Resource methods
	public boolean addResource(String resourceName) {
		if (!hasResource(resourceName)) {
			HashMap<String, Integer> userLocks = new HashMap<String, Integer>();

			userLockMap.put(resourceName, userLocks);
			return true;
		} else {
			return false;
		}
	}
	
	public boolean removeResource(String resourceName) {
		if (hasResource(resourceName)) {
			userLockMap.remove(resourceName);
			
			return false;
		} else {
			return false;
		}
	}

	
	//User methods
	public boolean addUser(ActorRef user, String resourceName) {
		if (hasResource(resourceName)) {
			HashMap<String, Integer> userLocks = userLockMap.get(resourceName);

			if (userLocks.containsKey(user.toString())) {
				Integer numLocks = userLocks.get(user.toString());
				userLocks.replace(user.toString(), numLocks++);
			} else {
				userLocks.put(user.toString(), 1);
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean removeUser(ActorRef user, String resourceName) {
		if (hasResource(resourceName)) {
			HashMap<String, Integer> userLocks = userLockMap.get(resourceName);

			if (userLocks.containsKey(user.toString())) {
				Integer numLocks = userLocks.get(user.toString());
				userLocks.replace(user.toString(), numLocks--);

				if (numLocks == 0) {
					userLocks.remove(user.toString());
				}

				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	
	//Util methods
	public boolean hasUsers(String resourceName) {
		if (hasResource(resourceName)) {
			return !userLockMap.get(resourceName).isEmpty();
		} else {
			return false;
		}
	}
	
	public boolean hasUser(ActorRef user, String resourceName) {
		if (hasResource(resourceName)) {
			return userLockMap.get(resourceName).containsKey(user.toString());
		} else {
			return false;
		}
	}
	
	public boolean isOnlyUser(ActorRef user, String resourceName) {		
		if (hasResource(resourceName)) {
			HashMap<String, Integer> userLocks = userLockMap.get(resourceName);
			
			if (userLocks.size() != 1) {
				return false;
			} else {
				return userLocks.containsKey(user.toString());
			}
		} else {
			return false;
		}
	}
	
	private boolean hasResource(String resourceName) {
		return userLockMap.containsKey(resourceName);
	}
}
