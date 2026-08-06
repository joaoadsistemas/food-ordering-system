package com.food.ordering.system.order.service.domain.ports.output.ai.order.noteInterpreter;

import com.food.ordering.system.domain.valueObject.OrderPreferences;

public interface OrderNoteInterpreter {
   OrderPreferences interpret(String orderNotes);
}
