package cmsc433.p2;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Simulation is the main class used to run the simulation.  You may
 * add any fields (static or instance) or any methods you wish.
 */

public class Simulation {
	// List to track simulation events during simulation
	public static List<SimulationEvent> events;  


	//Number to keep track of how many tables are taken
	private static Integer takenTables = 0;
	//A list of orders that are currently available
	private static HashMap<Integer, List<Food>> orders = new HashMap<Integer, List<Food>>();
	//A list of order numbers
	private static Queue<Integer> orderNums = new LinkedList<Integer>();
	//A list of completed orders that need to be picked up
	private static HashMap<Integer, List<Food>> completedOrders = new HashMap<Integer, List<Food>>();
	//How many people  the Ratsie's can hold
	private static int capacity;

	private static Machine fountainMachine;;
	private static Machine ovenMachine;
	private static Machine fryerMachine;
	private static Machine grillpressMachine;

	private static Object eventLock = new Object();
	/**
	 * Used by other classes in the simulation to log events
	 * @param event
	 */
	public static void logEvent(SimulationEvent event) {
		synchronized (eventLock) {
			events.add(event);
			System.out.println(event);
		}
	}

	/**
	 * 	Function responsible for performing the simulation. Returns a List of 
	 *  SimulationEvent objects, constructed any way you see fit. This List will
	 *  be validated by a call to Validate.validateSimulation. This method is
	 *  called from Simulation.main(). We should be able to test your code by 
	 *  only calling runSimulation.
	 *  
	 *  Parameters:
	 *	@param numCustomers the number of customers wanting to enter Ratsie's
	 *	@param numCooks the number of cooks in the simulation
	 *	@param numTables the number of tables in Ratsie's (i.e. Ratsie's capacity)
	 *	@param machineCapacity the capacity of all machines in Ratsie's
	 *  @param randomOrders a flag say whether or not to give each customer a random order
	 *
	 */
	public static List<SimulationEvent> runSimulation(
			int numCustomers, int numCooks,
			int numTables, 
			int machineCapacity,
			boolean randomOrders
			) {

		//This method's signature MUST NOT CHANGE.  


		//We are providing this events list object for you.  
		//  It is the ONLY PLACE where a concurrent collection object is 
		//  allowed to be used.
		events = Collections.synchronizedList(new ArrayList<SimulationEvent>());




		// Start the simulation
		logEvent(SimulationEvent.startSimulation(numCustomers,
				numCooks,
				numTables,
				machineCapacity));



		// Set things up you might need
		capacity = numTables;

		// Start up machines
		fountainMachine = new Machine(Machine.MachineType.fountain, FoodType.soda, machineCapacity);
		ovenMachine = new Machine(Machine.MachineType.oven, FoodType.pizza, machineCapacity);
		fryerMachine = new Machine(Machine.MachineType.fryer, FoodType.wings, machineCapacity);
		grillpressMachine = new Machine(Machine.MachineType.grillPress, FoodType.sub, machineCapacity);


		// Let cooks in
		Thread[] cooks = new Thread[numCooks];
		for (int i = 0; i < cooks.length; i++) {
			cooks[i] = new Thread(new Cook("Cook " + (i)));
			cooks[i].start();
		}

		// Build the customers.
		Thread[] customers = new Thread[numCustomers];
		LinkedList<Food> order;
		if (!randomOrders) {
			order = new LinkedList<Food>();
			order.add(FoodType.wings);
			order.add(FoodType.pizza);
			order.add(FoodType.sub);
			order.add(FoodType.soda);
			for(int i = 0; i < customers.length; i++) {
				customers[i] = new Thread(
						new Customer("Customer " + (i), order)
						);
			}
		}
		else {
			for(int i = 0; i < customers.length; i++) {
				Random rnd = new Random();
				int wingsCount = rnd.nextInt(4);
				int pizzaCount = rnd.nextInt(4);
				int subCount = rnd.nextInt(4);
				int sodaCount = rnd.nextInt(4);
				order = new LinkedList<Food>();
				for (int b = 0; b < wingsCount; b++) {
					order.add(FoodType.wings);
				}
				for (int f = 0; f < pizzaCount; f++) {
					order.add(FoodType.pizza);
				}
				for (int f = 0; f < subCount; f++) {
					order.add(FoodType.sub);
				}
				for (int c = 0; c < sodaCount; c++) {
					order.add(FoodType.soda);
				}
				customers[i] = new Thread(
						new Customer("Customer " + (i+1), order)
						);
			}
		}



		// Now "let the customers know the shop is open" by
		//    starting them running in their own thread.
		for(int i = 0; i < customers.length; i++) {
			customers[i].start();
			//NOTE: Starting the customer does NOT mean they get to go
			//      right into the shop.  There has to be a table for
			//      them.  The Customer class' run method has many jobs
			//      to do - one of these is waiting for an available
			//      table...
		}


		try {
			// Wait for customers to finish
			//   -- you need to add some code here...
			for (int i = 0; i < customers.length; i++) {
				customers[i].join();
			}





			// Then send cooks home...
			// The easiest way to do this might be the following, where
			// we interrupt their threads.  There are other approaches
			// though, so you can change this if you want to.
			for(int i = 0; i < cooks.length; i++)
				cooks[i].interrupt();
			for(int i = 0; i < cooks.length; i++)
				cooks[i].join();

		}
		catch(InterruptedException e) {
			System.out.println("Simulation thread interrupted.");
		}

		// Shut down machines
		logEvent(SimulationEvent.machineEnding(fountainMachine));
		logEvent(SimulationEvent.machineEnding(fryerMachine));
		logEvent(SimulationEvent.machineEnding(grillpressMachine));
		logEvent(SimulationEvent.machineEnding(ovenMachine));




		// Done with simulation		
		logEvent(SimulationEvent.endSimulation());

		return events;
	}

	//Start customer functions
	public static boolean areTablesAvailable() {
		synchronized (takenTables) {
			return takenTables < capacity;
		}
	}

	public static void enterRatsies() throws InterruptedException {
		synchronized (takenTables) {
			//Wait if there are not open tables
			//			while (takenTables >= capacity)
			//				takenTables.wait();

			takenTables++;
		}
	}

	public static void placeOrder(Integer orderNum, List<Food> order) {
		synchronized (orderNums) {
			orderNums.add(orderNum);

			synchronized (orders) {
				orders.put(orderNum, order);
			}

			orderNums.notifyAll();
		}
	}

	public static List<Food> receiveOrder(Integer orderNum) throws InterruptedException {
		synchronized (completedOrders) {
			//Wait if order is not done yet
			while (!completedOrders.containsKey(orderNum))
				completedOrders.wait();

			return completedOrders.remove(orderNum);
		}
	}

	public static void leaveRatsies() {
		synchronized (takenTables) {
			takenTables--;

			//Notify other customers that there are open tables
			//			takenTables.notifyAll();
		}
	}
	//End customer functions

	//Start cook functions
	public static boolean isOrderReady(Integer orderNumber) {
		synchronized (completedOrders) { 
			return completedOrders.containsKey(orderNumber);
		}
	}

	public static int getOrderNum() throws InterruptedException {
		synchronized (orderNums) {
			//Wait if there are no orders
			while (orderNums.isEmpty())
				orderNums.wait();

			return orderNums.remove();
		}
	}

	public static List<Food> getOrder(int orderNumber) {
		synchronized (orders) {
			return orders.get(orderNumber);
		}
	}

	public static boolean areOrdersReady() {
		synchronized (orders) {
			return !orders.isEmpty();
		}
	}

	public static Object cookFood(Food food) throws InterruptedException {
		Machine machine = null;

		switch (food.toString()) {
		case "wings":
			machine = fryerMachine;
			break;
		case "pizza":
			machine = ovenMachine;
			break;
		case "soda":
			machine = fountainMachine;
			break;
		case "sub":
			machine = grillpressMachine;
			break;
		}

		return machine.makeFood(food);
	}

	public static void completeOrder(int orderNum, List<Food> order) {
		synchronized (completedOrders) {
			completedOrders.put(orderNum, order);

			completedOrders.notifyAll();
		}
	}
	//End cook functions

	/**
	 * Entry point for the simulation.
	 *
	 * @param args the command-line arguments for the simulation.  There
	 * should be exactly four arguments: the first is the number of customers,
	 * the second is the number of cooks, the third is the number of tables
	 * in Ratsie's, and the fourth is the number of items each machine
	 * can make at the same time.  
	 */
	public static void main(String args[]) throws InterruptedException {
		// Parameters to the simulation
		/*
		if (args.length != 4) {
			System.err.println("usage: java Simulation <#customers> <#cooks> <#tables> <capacity> <randomorders");
			System.exit(1);
		}
		int numCustomers = new Integer(args[0]).intValue();
		int numCooks = new Integer(args[1]).intValue();
		int numTables = new Integer(args[2]).intValue();
		int machineCapacity = new Integer(args[3]).intValue();
		boolean randomOrders = new Boolean(args[4]);
		 */
		int numCustomers = 10;
		int numCooks =6;
		int numTables = 5;
		int machineCapacity = 1;
		boolean randomOrders = true;


		// Run the simulation and then 
		//   feed the result into the method to validate simulation.
		System.out.println("Did it work? " + 
				Validate.validateSimulation(
						runSimulation(
								numCustomers, numCooks, 
								numTables, machineCapacity,
								randomOrders
								)
						)
				);
	}

}



