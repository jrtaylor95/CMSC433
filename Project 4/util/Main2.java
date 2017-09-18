package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import actors.SimulationManagerActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import enums.*;

import messages.SimulationFinishMsg;
import messages.SimulationStartMsg;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * Sample class for setting up and running a resource-manager system.
 * 
 * Feel free to modify as you wish, but do not include any code that the rest of
 * your implementation depends on.
 * 
 * @author Rance Cleaveland
 *
 */
public class Main2 {

	ActorSystem system = ActorSystem.create("Resource manager system");

	public static void main(String[] args) {
		
		PrintStream fileOutput;
		try {
			fileOutput = new PrintStream(new File("/Users/jtaylor/Desktop/output.txt"));
			System.setOut(fileOutput);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// Create actor system and instantiate a simulation manager.
		
		ActorSystem system = ActorSystem.create("Simulation");
		ArrayList<NodeSpecification> nodes = setupTest3();  // Can change this to others below.
		ActorRef simulationManager = SimulationManagerActor.makeSimulationManager(nodes, system);
		
		// Start simulation manager and retrieve result
		
		long futureDelay = 1000L;  // milliseconds
		Duration awaitDelay = Duration.Inf();

		Future<Object> fmsg = Patterns.ask(simulationManager, new SimulationStartMsg(), futureDelay);
		SimulationFinishMsg msg = null;
		try {
			msg = (SimulationFinishMsg)Await.result(fmsg, awaitDelay);
		}
		catch (Exception e) {
			System.out.println(e);
		}
		
		// When each users has finished, terminate
		system.terminate();
		
		// Get future that returns result when system has terminated.
		Future<Terminated> term = system.whenTerminated();
		try {
			Await.result(term, awaitDelay);
		}
		catch (Exception e) {
			System.out.println(e);
		}
		
		// It is critical not to examine the log until after the actor system has shutdown. Otherwise, the log
		// may still be being modified as ResourceManagers send messages to the LoggerActor.
		for (Object o : msg.getLog())
			System.out.println(o);
	}

	/**
	 * This sets up one simple test system containing two nodes, each with one
	 * user.
	 * 
	 * @return	System specification consisting of list of node specs
	 */
	private static ArrayList<NodeSpecification> setupTest1 () {
		
		// Create initial resources
		
		ArrayList<Resource> printers = Systems.makeResources("Printer", 2);
		ArrayList<Resource> scanners = Systems.makeResources("Scanner", 1);
		
		// Create user scripts
		
//		ArrayList<Object> list1 = new ArrayList<Object>();
//		list1.add(new AccessRequest("Printer_0", AccessRequestType.EXCLUSIVE_WRITE_BLOCKING));
//		list1.add(new AccessRequest("Scanner_0", AccessRequestType.CONCURRENT_READ_NONBLOCKING));
//		
//		ArrayList<Object> list2 = new ArrayList<Object>();
//		list2.add(new AccessRelease("Printer_0", AccessType.EXCLUSIVE_WRITE));
//		list2.add(new AccessRelease("Scanner_0", AccessType.CONCURRENT_READ));
		
		UserScript script1;
		UserScript script2;
		try {
			script1 = UserScript.fromFile("/Users/jtaylor/Documents/School/CMSC433/p4/scripts/test1script1.txt");
			script2 = UserScript.fromFile("/Users/jtaylor/Documents/School/CMSC433/p4/scripts/test1script2.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.out.println(".\\scripts\\test1script1.txt not found");
			return null;
		}
		
		// Create node specifications
		
		ArrayList<UserScript> scriptList1 = new ArrayList<UserScript>();
		scriptList1.add(script1);
		NodeSpecification node1 = new NodeSpecification(printers, scriptList1);
		
		ArrayList<UserScript> scriptList2 = new ArrayList<UserScript>();
		scriptList2.add(script2);
		NodeSpecification node2 = new NodeSpecification(scanners, scriptList2);
		
		// Return list of nodes
		
		ArrayList<NodeSpecification> list = new ArrayList<NodeSpecification> ();
		list.add(node1);
		list.add(node2);
		return list;
	}
	
	/**
	 * Second sample system, with two nodes, each with one user.  One user has an
	 * empty script.
	 * 
	 * @return	System specification containing list of node specs.
	 */
	private static ArrayList<NodeSpecification> setupTest2 () {
//		ArrayList<Object> list1 = new ArrayList<Object> ();
//		list1.add(new AccessRequest("Printer_0", AccessRequestType.CONCURRENT_READ_BLOCKING));
//		list1.add(new ManagementRequest("Printer_0", ManagementRequestType.ADD));
//		list1.add(new AccessRequest("Printer_0", AccessRequestType.CONCURRENT_READ_BLOCKING));
//		list1.add(new ManagementRequest("Printer_0", ManagementRequestType.ENABLE));
//		list1.add(new AccessRequest("Printer_0", AccessRequestType.CONCURRENT_READ_BLOCKING));
//		list1.add(new AccessRelease("Printer_0", AccessType.CONCURRENT_READ));
		
		ArrayList<UserScript> scriptList1 = new ArrayList<UserScript> ();
		try {
			scriptList1.add(UserScript.fromFile("/Users/jtaylor/Documents/School/CMSC433/p4/scripts/test2script.txt"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		NodeSpecification node1 = new NodeSpecification(new ArrayList<Resource>(), scriptList1);
		NodeSpecification node2 = new NodeSpecification(new ArrayList<Resource>(), new ArrayList<UserScript>());
		
		ArrayList<NodeSpecification> list = new ArrayList<NodeSpecification> ();
		list.add(node1);
		list.add(node2);
		return list;
	}
	
	private static ArrayList<NodeSpecification> setupTest3 () {
		ArrayList<Resource> printers = Systems.makeResources("Printer", 1);
		ArrayList<Resource> scanners = Systems.makeResources("Scanner", 1);
		
		ArrayList<UserScript> scriptList1 = new ArrayList<UserScript> ();
		try {
			scriptList1.add(UserScript.fromFile("/Users/jtaylor/Documents/School/CMSC433/p4/scripts/test3script1.txt"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<UserScript> scriptList2 = new ArrayList<UserScript> ();
		try {
			scriptList2.add(UserScript.fromFile("/Users/jtaylor/Documents/School/CMSC433/p4/scripts/test3script2.txt"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		NodeSpecification node1 = new NodeSpecification(printers, scriptList1);
		NodeSpecification node2 = new NodeSpecification(scanners, scriptList2);
		
		ArrayList<NodeSpecification> list = new ArrayList<NodeSpecification> ();
		list.add(node1);
		list.add(node2);
		return list;
	}
}
