package com.aspireserver.duelbot.fsm.states;

import com.aspireserver.duelbot.fsm.BotState;
import com.aspireserver.duelbot.fsm.State;
import com.aspireserver.duelbot.npc.CombatTrait;

/** Airborne over a drop/void: place a save block a tick before the fatal threshold. */
public final class ClutchState implements State {

    @Override
    public BotState id() {
        return BotState.CLUTCH;
    }

    @Override
    public void tick(CombatTrait trait) {
        trait.tickClutch();
    }
}
