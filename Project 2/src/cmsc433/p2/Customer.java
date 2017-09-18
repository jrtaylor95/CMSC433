package cmsc433.p2;

import java.util.List;

/**
 * Customers are simulation actors that have two fields: a name, and a list
 * of Food items that constitute the Customer's order.  When running, an
 * customer attempts to enter the Ratsie's (only successful if the
 * Ratsie's has a free table), place its order, and then leave the
 * Ratsie's when the order is complete.
 */
public class Customer implements Runnable {
	//JUST ONE SET OF IDEAS ON HOW TO SET THINGS UP...
	private final String name;
	private final List<Food> order;
	private final int orderNum;    

	private static int runningCounter = 0;
	private static Object ratsiesLock = new Object();

	/**
	 * You can feel free modify this constructor.  It must take at
	 * least the name and order but may take other parameters if you
	 * would find adding them useful.
	 */
	public Customer(String name, List<Food> order) {
		this.name = name;
		this.order = order;
		this.orderNum = ++runningCounter;
	}

	public String toString() {
		return name;
	}

	/** 
	 * This method defines what an Customer does: The customer attempts to
	 * enter the Ratsie's (only successful when the Ratsie's has a
	 * free table), place its order, and then leave the Ratsie's
	 * when the order is complete.
	 */
	public void run(){

		try { 
			//YOUR CODE GOES HERE...

			Simulation.logEvent(SimulationEvent.customerStarting(this));

			//Attempt to enter Ratsie's
			synchronized (ratsiesLock) {
				while (!Simulation.areTablesAvailable())
					ratsiesLock.wait();

				//Enter Ratsie's
				Simulation.enterRatsies();
				Simulation.logEvent(SimulationEvent.customerEnteredRatsies(this));
			}
			//Attempt to place an order
			Simulation.logEvent(SimulationEvent.customerPlacedOrder(this, order, orderNum));
			Simulation.placeOrder(orderNum, order);

			//Wait for order
//			while (!Simulation.isOrderReady(orderNum))
//				wait();

			//Receive order
			Simulation.receiveOrder(orderNum);	
			Simulation.logEvent(SimulationEvent.customerReceivedOrder(this, order, orderNum));
			//Leave
			synchronized (ratsiesLock) {
				Simulation.logEvent(SimulationEvent.customerLeavingRatsies(this));
				Simulation.leaveRatsies();
				ratsiesLock.notifyAll();
			}
		} catch (InterruptedException e) {}
	}

	public String getName() {
		return name;
	}

	public List<Food> getOrder() {
		return order;
	}

	public int getOrderNum() {
		return orderNum;
	}
}