package cmsc433.p2;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooks are simulation actors that have at least one field, a name.
 * When running, a cook attempts to retrieve outstanding orders placed
 * by Eaters and process them.
 */
public class Cook implements Runnable {
	private final String name;
	private List<Food> completedFood;
	/**
	 * You can feel free to modify this constructor.  It must
	 * take at least the name, but may take other parameters
	 * if you would find adding them useful. 
	 *
	 * @param: the name of the cook
	 */
	public Cook(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}

	/**
	 * This method executes as follows.  The cook tries to retrieve
	 * orders placed by Customers.  For each order, a List<Food>, the
	 * cook submits each Food item in the List to an appropriate
	 * Machine, by calling makeFood().  Once all machines have
	 * produced the desired Food, the order is complete, and the Customer
	 * is notified.  The cook can then go to process the next order.
	 * If during its execution the cook is interrupted (i.e., some
	 * other thread calls the interrupt() method on it, which could
	 * raise InterruptedException if the cook is blocking), then it
	 * terminates.
	 */
	public void run() {

		Simulation.logEvent(SimulationEvent.cookStarting(this));

		try {
			while(true) {
				//YOUR CODE GOES HERE...

				//wait until an order comes in
				//				while (!Simulation.areOrdersReady())
				//					wait();
				completedFood = new ArrayList<Food>();
				
				//Start an order
				int orderNumber = Simulation.getOrderNum();
				List<Food> order = Simulation.getOrder(orderNumber);
				Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, order, orderNumber));

				//Submit request to food machine(s)
				Thread[] foodTimers = new Thread[order.size()];
				int i = 0;
				for (Food food : order) {
					foodTimers[i] = new Thread(new SetFoodTimer(food, orderNumber));
					foodTimers[i].start();
					i++;
				}
				
				for (int j = 0; j < order.size(); j++) {
					foodTimers[j].join();
				}

				//Receive completed food item	

				//Complete order
				Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, orderNumber));
				Simulation.completeOrder(orderNumber, order);
				
			}
		}
		catch(InterruptedException e) {
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}

	private class SetFoodTimer implements Runnable {
		Food food;
		int orderNumber;

		public SetFoodTimer(Food food, int orderNumber) {
			this.food = food;
			this.orderNumber = orderNumber;
		}

		public void run() {
			try {
				//YOUR CODE GOES HERE...
				Simulation.logEvent(SimulationEvent.cookStartedFood(Cook.this, food, orderNumber));
				Object foodTimer = Simulation.cookFood(food);
				
				synchronized (foodTimer) {
					foodTimer.wait();
				}
				synchronized (completedFood) {
					completedFood.add(food);
					Simulation.logEvent(SimulationEvent.cookFinishedFood(Cook.this, food, orderNumber));
				}
				
			} catch(InterruptedException e) { }
		}
	}

}