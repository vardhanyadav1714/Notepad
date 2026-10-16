package `in`.innovaticshub.notepad.ui.canvas.drawing

import android.view.MotionEvent
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint

/** Read-only MotionEvent metadata not exposed by this Compose version. */
internal class HardwareInkSamples {
    var samples:List<StrokePoint> = emptyList(); private set
    var barrelPressed=false; private set
    var stylusNearby=false; private set
    // Latched for the complete Android touch sequence, even if a Compose handler restarts
    // or a second finger goes down and up between two delivered pointer frames.
    var multiTouchGesture=false; private set
    var cancelled=false; private set
    fun observe(event:MotionEvent) {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {multiTouchGesture=false;cancelled=false}
        if(event.actionMasked==MotionEvent.ACTION_CANCEL) cancelled=true
        if((0 until event.pointerCount).count {event.getToolType(it)==MotionEvent.TOOL_TYPE_FINGER}>=2) multiTouchGesture=true
        barrelPressed=event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)!=0
        if(event.actionMasked==MotionEvent.ACTION_HOVER_EXIT) stylusNearby=false
        val index=(0 until event.pointerCount).firstOrNull { event.getToolType(it)==MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it)==MotionEvent.TOOL_TYPE_ERASER }
        if(index==null) { samples=emptyList(); return }
        if(event.actionMasked==MotionEvent.ACTION_HOVER_ENTER || event.actionMasked==MotionEvent.ACTION_HOVER_MOVE) stylusNearby=true
        samples=buildList {
            for(history in 0 until event.historySize) add(StrokePoint(event.getHistoricalX(index,history),event.getHistoricalY(index,history),
                event.getHistoricalPressure(index,history).coerceIn(0f,1f),event.getHistoricalEventTime(history),
                event.getHistoricalAxisValue(MotionEvent.AXIS_TILT,index,history),event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION,index,history)))
            add(StrokePoint(event.getX(index),event.getY(index),event.getPressure(index).coerceIn(0f,1f),event.eventTime,
                event.getAxisValue(MotionEvent.AXIS_TILT,index),event.getAxisValue(MotionEvent.AXIS_ORIENTATION,index)))
        }
    }
}
