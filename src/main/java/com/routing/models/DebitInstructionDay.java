package com.routing.models;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable
public class DebitInstructionDay {
    private int debInstructSelectedDay;

    public int getDebInstructSelectedDay() {
        return debInstructSelectedDay;
    }

    public void setDebInstructSelectedDay(int debInstructSelectedDay) {
        this.debInstructSelectedDay = debInstructSelectedDay;
    }
}