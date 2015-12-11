package cc.sferalabs.sfera.drivers.easyvr.events;

import cc.sferalabs.sfera.events.BooleanEvent;
import cc.sferalabs.sfera.events.Node;

public class ConnectionEvent extends BooleanEvent implements EasyVREvent {

	public ConnectionEvent(Node source, Boolean value) {
		super(source, "connection", value);
	}
}
