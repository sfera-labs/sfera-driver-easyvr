package cc.sferalabs.sfera.drivers.easyvr.events;

import cc.sferalabs.sfera.drivers.easyvr.EasyVR;
import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.NumberEvent;

/**
 * Event triggered after speaker-dependent custom recognition command
 * {@link EasyVR#recognizeCommand(int)}.
 * 
 * @sfera.event.id recog.sd(group) the group index (0 = trigger, 1-15 = generic,
 *                 16 = password)
 * @sfera.event.value positive integer corresponding to the recognized command
 *                    position or negative integer corresponding to the
 *                    recognition error
 * 
 * @author Ulderico Arcidiaco
 *
 * @version 1.0.0
 *
 */
public class RecogSDEvent extends NumberEvent implements EasyVREvent {

	public RecogSDEvent(Node source, int index, Integer value) {
		super(source, "recog.sd(" + index + ")", value);
	}
}
