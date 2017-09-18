package cmsc433.p2;

import java.util.List;

import sun.misc.Queue;

public class Ratsies {

	
	private int capacity;
	private static int takenTables = 0;
	private static Queue<List<Food>> orders = new Queue<List<Food>>();
	protected Ratsies(int capacity) {
		this.capacity = capacity;
	}
	
	private static Ratsies instance = null;
	
	public static Ratsies getInstance(int capacity) {
		if (instance == null)
			instance = new Ratsies(capacity);
		return instance;
	}
	public static Ratsies getInstance() {
		return instance;
	}
	
	public boolean isTableAvailable() {
		return takenTables < capacity;
	}
	
	public void enterRatsies() {
		takenTables++;
	}
	
	public void leaveRatsies() {
		takenTables--;
	}
	
	public void addOrder(List<Food> order) {
		orders.enqueue(order);
	}
	
	public List<Food> takeOrder() throws InterruptedException {
		return orders.dequeue();
	}
}
