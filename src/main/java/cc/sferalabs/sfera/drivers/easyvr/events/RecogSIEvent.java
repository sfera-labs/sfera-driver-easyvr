package cc.sferalabs.sfera.drivers.easyvr.events;

import cc.sferalabs.sfera.drivers.easyvr.EasyVR;
import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.NumberEvent;

/**
 * Event triggered after speaker-independent word recognition command
 * {@link EasyVR#recognizeWord(int)}.
 * 
 * @sfera.event.id recog.sd(group)
 * @sfera.event.value positive integer corresponding to the recognized word
 *                    position or negative integer corresponding to the
 *                    recognition error
 * 
 * @author Ulderico Arcidiaco
 *
 * @version 1.0.0
 *
 */
public class RecogSIEvent extends NumberEvent implements EasyVREvent {

	public RecogSIEvent(Node source, int index, Integer value) {
		super(source, "recog.si(" + index + ")", value);
	}
}
